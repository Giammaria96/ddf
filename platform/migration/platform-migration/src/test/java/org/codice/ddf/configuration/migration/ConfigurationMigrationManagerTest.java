/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.configuration.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.common.base.Charsets;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.management.MBeanException;
import javax.management.ObjectName;
import org.apache.commons.io.FileUtils;
import org.apache.karaf.system.SystemService;
import org.codice.ddf.migration.Migratable;
import org.codice.ddf.migration.MigrationException;
import org.codice.ddf.migration.MigrationInformation;
import org.codice.ddf.migration.MigrationMessage;
import org.codice.ddf.migration.MigrationReport;
import org.codice.ddf.migration.MigrationWarning;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.StringDescription;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationMigrationManagerTest extends AbstractMigrationSupport {

  public static final String TEST_BRANDING = "test";

  public static final String TEST_VERSION = "1.0";

  public static final String TEST_MESSAGE = "Test message.";

  public static final String TEST_DIRECTORY = "exported";

  private static final String WRAPPER_KEY = "wrapper.key";

  private static final String RESTART_JVM = "karaf.restart.jvm";

  private ConfigurationMigrationManager configurationMigrationManager;

  private List<Migratable> migratables;

  @Mock private SystemService mockSystemService;

  @Mock private WrapperManagerMXBean mockWrapperManager;

  @Before
  public void setup() throws Exception {
    migratables = Collections.emptyList();
    ManagementFactory.getPlatformMBeanServer()
        .registerMBean(
            mockWrapperManager, new ObjectName("org.tanukisoftware.wrapper:type=WrapperManager"));
    doNothing().when(mockWrapperManager).restart();
  }

  @After
  public void tearDown() throws Exception {
    System.getProperties().remove(RESTART_JVM);
    System.getProperties().remove(WRAPPER_KEY);
    ManagementFactory.getPlatformMBeanServer()
        .unregisterMBean(new ObjectName("org.tanukisoftware.wrapper:type=WrapperManager"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void constructorWithNullConfigurationMigratablesList() {
    new ConfigurationMigrationManager(null, mockSystemService);
  }

  @Test(expected = IllegalArgumentException.class)
  public void constructorWithNullSystemService() {
    new ConfigurationMigrationManager(new ArrayList<>(), null);
  }

  @Test
  public void constructorWithoutProductBrandingFile() throws Exception {
    createVersionFile();

    thrown.expect(IOError.class);
    thrown.expectMessage("Branding.txt");
    thrown.expectCause(Matchers.instanceOf(NoSuchFileException.class));

    new ConfigurationMigrationManager(new ArrayList<>(), mockSystemService);
  }

  @Test
  public void constructorWithoutProductVersionFile() throws Exception {
    createBrandingFile();

    thrown.expect(IOError.class);
    thrown.expectMessage("Version.txt");
    thrown.expectCause(Matchers.instanceOf(NoSuchFileException.class));

    new ConfigurationMigrationManager(new ArrayList<>(), mockSystemService);
  }

  @Test
  public void constructorWithMissingProductBranding() throws Exception {
    createBrandingFile("");
    createVersionFile();

    thrown.expect(IOError.class);
    thrown.expectMessage("missing product branding information");

    new ConfigurationMigrationManager(new ArrayList<>(), mockSystemService);
  }

  @Test
  public void constructorWithMissingProductVersion() throws Exception {
    createBrandingFile();
    createVersionFile("");

    thrown.expect(IOError.class);
    thrown.expectMessage("missing product version information");

    new ConfigurationMigrationManager(new ArrayList<>(), mockSystemService);
  }

  @Test
  public void doExportSucceeds() throws Exception {
    configurationMigrationManager = spy(getConfigurationMigrationManager());

    MigrationReport report =
        configurationMigrationManager.doExport(ddfHome.resolve(TEST_DIRECTORY));

    assertThat("Export was not successful", report.wasSuccessful(), is(true));
  }

  @Test
  public void doExportSucceedsWithConsumer() throws Exception {
    final Consumer<MigrationMessage> consumer = mock(Consumer.class);

    configurationMigrationManager = spy(getConfigurationMigrationManager());

    MigrationReport report =
        configurationMigrationManager.doExport(ddfHome.resolve(TEST_DIRECTORY), consumer);

    assertThat("Export was not successful", report.wasSuccessful(), is(true));
    verify(consumer, Mockito.atLeastOnce()).accept(Mockito.notNull());
  }

  @Test
  public void doExportSucceedsWithWarnings() throws Exception {
    final Path path = ddfHome.resolve(TEST_DIRECTORY);
    configurationMigrationManager = spy(getConfigurationMigrationManager());

    createDummyExport(Paths.get(path.toString(), TEST_BRANDING + "-" + TEST_VERSION + ".dar"));

    doAnswer(
            invocation -> {
              MigrationReport report = invocation.getArgument(0);
              report.record(new MigrationWarning(TEST_MESSAGE));
              return null;
            })
        .when(configurationMigrationManager)
        .delegateToExportMigrationManager(
            any(MigrationReportImpl.class), any(Path.class), any(CipherUtils.class));

    MigrationReport report = configurationMigrationManager.doExport(path);

    assertThat("Export was not successful", report.wasSuccessful(), is(true));
    reportHasWarningMessage(report.warnings(), equalTo(TEST_MESSAGE));
    reportHasWarningMessage(
        report.warnings(),
        equalTo(
            "Successfully exported to file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar] with warnings; make sure to review."));
    verify(configurationMigrationManager)
        .delegateToExportMigrationManager(
            any(MigrationReportImpl.class), any(Path.class), any(CipherUtils.class));
  }

  @Test(expected = IllegalArgumentException.class)
  public void doExportWithNullPath() throws Exception {
    configurationMigrationManager = getConfigurationMigrationManager();

    configurationMigrationManager.doExport((Path) null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void doExportWithNullConsumer() throws Exception {
    configurationMigrationManager = getConfigurationMigrationManager();

    configurationMigrationManager.doExport(ddfHome.resolve(TEST_DIRECTORY), null);
  }

  @Test
  public void doExportFailsToCreateDirectory() throws Exception {
    final Path path = ddfHome.resolve("invalid-directory");

    path.toFile().createNewFile(); // create it as a file

    configurationMigrationManager = getConfigurationMigrationManager();

    MigrationReport report = configurationMigrationManager.doExport(path);

    reportHasErrorMessage(
        report.errors(),
        Matchers.matchesPattern(
            "Unexpected error: unable to create directory \\["
                + path.toString().replace("\\", "\\\\")
                + "\\]; .*\\."));
  }

  @Test
  public void doExportRecordsErrorForMigrationException() throws Exception {
    configurationMigrationManager = spy(getConfigurationMigrationManager());

    doThrow(new MigrationException(TEST_MESSAGE))
        .when(configurationMigrationManager)
        .delegateToExportMigrationManager(
            any(MigrationReportImpl.class), any(Path.class), any(CipherUtils.class));

    MigrationReport report =
        configurationMigrationManager.doExport(ddfHome.resolve(TEST_DIRECTORY));

    reportHasErrorMessage(report.errors(), equalTo(TEST_MESSAGE));
    verify(configurationMigrationManager)
        .delegateToExportMigrationManager(
            any(MigrationReportImpl.class), any(Path.class), any(CipherUtils.class));
  }

  @Test
  public void doExportRecordsErrorForIOException() throws Exception {
    final Path path = ddfHome.resolve(TEST_DIRECTORY);

    configurationMigrationManager = spy(getConfigurationMigrationManager());

    doThrow(new IOException("testing"))
        .when(configurationMigrationManager)
        .delegateToExportMigrationManager(
            any(MigrationReportImpl.class), any(Path.class), any(CipherUtils.class));

    MigrationReport report = configurationMigrationManager.doExport(path);

    reportHasErrorMessage(
        report.errors(),
        equalTo(
            "Export error: failed to close export file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar]; testing."));
    verify(configurationMigrationManager)
        .delegateToExportMigrationManager(
            any(MigrationReportImpl.class), any(Path.class), any(CipherUtils.class));
  }

  @Test
  public void doExportRecordsErrorForRuntimeException() throws Exception {
    final Path path = ddfHome.resolve(TEST_DIRECTORY);

    configurationMigrationManager = spy(getConfigurationMigrationManager());

    doThrow(new RuntimeException("testing."))
        .when(configurationMigrationManager)
        .delegateToExportMigrationManager(
            any(MigrationReportImpl.class), any(Path.class), any(CipherUtils.class));

    MigrationReport report = configurationMigrationManager.doExport(path);

    reportHasErrorMessage(
        report.errors(),
        equalTo(
            "Unexpected internal error: failed to export to file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar]; testing."));
    verify(configurationMigrationManager)
        .delegateToExportMigrationManager(
            any(MigrationReportImpl.class), any(Path.class), any(CipherUtils.class));
  }

  @Test
  public void doImportSucceedsWithKarafRestart() throws Exception {
    final Path path = ddfHome.resolve(TEST_DIRECTORY);

    configurationMigrationManager = spy(getConfigurationMigrationManager());

    // Need to actually generate a zip with a valid checksum so calling export
    configurationMigrationManager.doExport(path);

    expectImportDelegationIsSuccessful();

    MigrationReport report = configurationMigrationManager.doImport(path);

    assertThat("Import was not successful", report.wasSuccessful(), is(true));
    assertThat(
        "Restart system property was not set", System.getProperty(RESTART_JVM), equalTo("true"));
    reportHasInfoMessage(
        report.infos(),
        equalTo(
            "Successfully imported from file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar]."));
    reportHasInfoMessage(
        report.infos(), equalTo("Restarting the system for changes to take effect."));
    verify(mockSystemService).reboot();
    verify(mockWrapperManager, Mockito.never()).restart();
    verify(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));
  }

  @Test
  public void doImportSucceedsWithWrapperRestart() throws Exception {
    System.setProperty(WRAPPER_KEY, "abc");
    final Path path = ddfHome.resolve(TEST_DIRECTORY);

    configurationMigrationManager = spy(getConfigurationMigrationManager());

    expectImportDelegationIsSuccessful();

    // Need to actually generate a zip with a valid checksum so calling export
    configurationMigrationManager.doExport(path);

    MigrationReport report = configurationMigrationManager.doImport(path);

    assertThat("Import was not successful", report.wasSuccessful(), is(true));
    assertThat("Restart system property was set", System.getProperty(RESTART_JVM), nullValue());
    reportHasInfoMessage(
        report.infos(),
        equalTo(
            "Successfully imported from file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar]."));
    reportHasInfoMessage(
        report.infos(), equalTo("Restarting the system for changes to take effect."));
    verify(mockSystemService, Mockito.never()).reboot();
    verify(mockWrapperManager).restart();
    verify(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));
  }

  @Test
  public void doImportSucceedsWithConsumer() throws Exception {
    final Path path = ddfHome.resolve(TEST_DIRECTORY);
    final Consumer<MigrationMessage> consumer = mock(Consumer.class);

    configurationMigrationManager = spy(getConfigurationMigrationManager());

    // Need to actually generate a zip with a valid checksum so calling export
    configurationMigrationManager.doExport(path);

    expectImportDelegationIsSuccessful();

    MigrationReport report = configurationMigrationManager.doImport(path, consumer);

    assertThat("Import was not successful", report.wasSuccessful(), is(true));
    assertThat(
        "Restart system property was not set", System.getProperty(RESTART_JVM), equalTo("true"));
    reportHasInfoMessage(
        report.infos(),
        equalTo(
            "Successfully imported from file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar]."));
    reportHasInfoMessage(
        report.infos(), equalTo("Restarting the system for changes to take effect."));
    verify(mockSystemService).reboot();
    verify(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));
    verify(mockWrapperManager, Mockito.never()).restart();
    verify(consumer, Mockito.atLeastOnce()).accept(Mockito.notNull());
  }

  @Test
  public void doImportSucceedsWithWarnings() throws Exception {
    final Path path = ddfHome.resolve(TEST_DIRECTORY);
    configurationMigrationManager = spy(getConfigurationMigrationManager());
    doAnswer(
            invocation -> {
              MigrationReport report = invocation.getArgument(0);
              report.record(new MigrationWarning(TEST_MESSAGE));
              return null;
            })
        .when(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));

    // Need to actually generate a zip with a valid checksum so calling export
    configurationMigrationManager.doExport(path);

    MigrationReport report = configurationMigrationManager.doImport(path);

    assertThat("Import was not successful", report.wasSuccessful(), is(true));
    reportHasWarningMessage(report.warnings(), equalTo(TEST_MESSAGE));
    reportHasWarningMessage(
        report.warnings(),
        equalTo(
            "Successfully imported from file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar] with warnings; make sure to review."));
    verify(mockWrapperManager, Mockito.never()).restart();
    verify(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));
  }

  @Test
  public void doImportSucceedsWithKarafAndFailsToReboot() throws Exception {
    final Path path = ddfHome.resolve(TEST_DIRECTORY);
    configurationMigrationManager = spy(getConfigurationMigrationManager());

    expectImportDelegationIsSuccessful();
    doThrow(Exception.class).when(mockSystemService).reboot();

    // Need to actually generate a zip with a valid checksum so calling export
    configurationMigrationManager.doExport(path);

    MigrationReport report = configurationMigrationManager.doImport(path);

    assertThat("Import was successful", report.wasSuccessful(), is(true));
    assertThat("Restart system property was set", System.getProperty(RESTART_JVM), equalTo("true"));
    reportHasInfoMessage(
        report.infos(),
        equalTo(
            "Successfully imported from file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar]."));
    reportHasInfoMessage(
        report.infos(), equalTo("Please restart the system for changes to take effect."));
    verify(mockSystemService).reboot();
    verify(mockWrapperManager, Mockito.never()).restart();
    verify(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));
  }

  @Test
  public void doImportSucceedsWithWrapperRestartAndFailsToReboot() throws Exception {
    System.setProperty(WRAPPER_KEY, "abc");
    final Path path = ddfHome.resolve(TEST_DIRECTORY);
    configurationMigrationManager = spy(getConfigurationMigrationManager());

    doThrow(new MBeanException(null)).when(mockWrapperManager).restart();
    expectImportDelegationIsSuccessful();

    // Need to actually generate a zip with a valid checksum so calling export
    configurationMigrationManager.doExport(path);

    MigrationReport report = configurationMigrationManager.doImport(path);

    assertThat("Import was successful", report.wasSuccessful(), is(true));
    assertThat("Restart system property was set", System.getProperty(RESTART_JVM), nullValue());
    reportHasInfoMessage(
        report.infos(),
        equalTo(
            "Successfully imported from file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar]."));
    reportHasInfoMessage(
        report.infos(), equalTo("Please restart the system for changes to take effect."));
    verify(mockSystemService, Mockito.never()).reboot();
    verify(mockWrapperManager).restart();
    verify(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));
  }

  @Test(expected = IllegalArgumentException.class)
  public void doImportWithNullPath() throws Exception {
    configurationMigrationManager = getConfigurationMigrationManager();

    configurationMigrationManager.doImport((Path) null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void doImportWithNullConsumer() throws Exception {
    configurationMigrationManager = getConfigurationMigrationManager();

    configurationMigrationManager.doImport(ddfHome.resolve(TEST_DIRECTORY), null);
  }

  @Test
  public void doImportRecordsErrorForMigrationException() throws Exception {
    configurationMigrationManager = spy(getConfigurationMigrationManager());
    final Path path = ddfHome.resolve(TEST_DIRECTORY);

    // Need to actually generate a zip with a valid checksum so calling export
    configurationMigrationManager.doExport(path);

    doThrow(new MigrationException(TEST_MESSAGE))
        .when(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));

    MigrationReport report =
        configurationMigrationManager.doImport(ddfHome.resolve(TEST_DIRECTORY));

    reportHasErrorMessage(report.errors(), equalTo(TEST_MESSAGE));
    verify(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));
    verify(mockWrapperManager, Mockito.never()).restart();
    verifyZeroInteractions(mockSystemService);
  }

  @Test
  public void doImportRecordsErrorForRuntimeException() throws Exception {
    final Path path = ddfHome.resolve(TEST_DIRECTORY);

    configurationMigrationManager = spy(getConfigurationMigrationManager());

    // Need to actually generate a zip with a valid checksum so calling export
    configurationMigrationManager.doExport(path);

    doThrow(new RuntimeException("testing"))
        .when(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));

    MigrationReport report = configurationMigrationManager.doImport(path);

    reportHasErrorMessage(
        report.errors(),
        equalTo(
            "Unexpected internal error: failed to import from file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar]; testing."));
    verify(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));
    verify(mockWrapperManager, Mockito.never()).restart();
    verifyZeroInteractions(mockSystemService);
  }

  @Test
  public void testDoImportRecordsMigrationExceptionWithInvalidChecksum() throws Exception {
    final Path path = ddfHome.resolve(TEST_DIRECTORY);

    configurationMigrationManager = spy(getConfigurationMigrationManager());

    configurationMigrationManager.doExport(path);

    Path checksumFilePath =
        Paths.get(
            path.toString(),
            TEST_BRANDING + "-" + TEST_VERSION + ".dar" + MigrationZipConstants.CHECKSUM_EXTENSION);

    FileUtils.writeStringToFile(checksumFilePath.toFile(), "invalid_checksum", Charsets.UTF_8);
    MigrationReport report = configurationMigrationManager.doImport(path);
    assertThat(report.wasSuccessful(), equalTo(false));
    reportHasErrorMessage(
        report.errors(),
        equalTo(
            "Import error: incorrect checksum for export file ["
                + path.resolve(TEST_BRANDING)
                + "-"
                + TEST_VERSION
                + ".dar]."));
  }

  private void expectImportDelegationIsSuccessful()
      throws NoSuchPaddingException, NoSuchAlgorithmException {
    doNothing()
        .when(configurationMigrationManager)
        .delegateToImportMigrationManager(
            any(MigrationReportImpl.class), any(MigrationZipFile.class));
  }

  private void reportHasInfoMessage(Stream<MigrationInformation> infos, Matcher<String> matcher) {
    final List<MigrationInformation> is = infos.collect(Collectors.toList());
    final long count = is.stream().filter((w) -> matcher.matches(w.getMessage())).count();
    final Description d = new StringDescription();

    matcher.describeTo(d);
    assertThat(
        "There are "
            + count
            + " matching info message(s) with "
            + d
            + " in the migration report.\nWarnings are: "
            + is.stream()
                .map(MigrationInformation::getMessage)
                .collect(Collectors.joining("\",\n\t\"", "[\n\t\"", "\"\n]")),
        count,
        equalTo(1L));
  }

  private void reportHasWarningMessage(Stream<MigrationWarning> warnings, Matcher<String> matcher) {
    final List<MigrationWarning> ws = warnings.collect(Collectors.toList());
    final long count = ws.stream().filter((w) -> matcher.matches(w.getMessage())).count();
    final Description d = new StringDescription();

    matcher.describeTo(d);
    assertThat(
        "There are "
            + count
            + " matching warning(s) with "
            + d
            + " in the migration report.\nWarnings are: "
            + ws.stream()
                .map(MigrationWarning::getMessage)
                .collect(Collectors.joining("\",\n\t\"", "[\n\t\"", "\"\n]")),
        count,
        equalTo(1L));
  }

  private void reportHasErrorMessage(Stream<MigrationException> errors, Matcher<String> matcher) {
    final List<MigrationException> es = errors.collect(Collectors.toList());
    final long count = es.stream().filter((e) -> matcher.matches(e.getMessage())).count();
    final Description d = new StringDescription();

    matcher.describeTo(d);

    assertThat(
        "There are "
            + count
            + " matching error(s) with "
            + d
            + "in the migration report.\nErrors are: "
            + es.stream()
                .map(MigrationException::getMessage)
                .collect(Collectors.joining("\",\n\t\"", "[\n\t\"", "\"\n]")),
        count,
        equalTo(1L));
  }

  private void createBrandingFile(String branding) throws IOException {
    final File brandingFile = new File(ddfHome.resolve("Branding.txt").toString());

    brandingFile.createNewFile();
    Files.write(brandingFile.toPath(), branding.getBytes(), StandardOpenOption.APPEND);
  }

  private void createBrandingFile() throws IOException {
    createBrandingFile(TEST_BRANDING);
  }

  private void createVersionFile(String version) throws IOException {
    File versionFile = new File(ddfHome.resolve("Version.txt").toString());
    versionFile.createNewFile();
    Files.write(versionFile.toPath(), version.getBytes(), StandardOpenOption.APPEND);
  }

  private void createVersionFile() throws IOException {
    createVersionFile(TEST_VERSION);
  }

  private ConfigurationMigrationManager getConfigurationMigrationManager() throws IOException {
    createBrandingFile();
    createVersionFile();
    return new ConfigurationMigrationManager(migratables, mockSystemService);
  }

  public static interface WrapperManagerMXBean {

    public void restart() throws MBeanException;
  }

  private void createDummyExport(Path exportPath)
      throws IOException, NoSuchPaddingException, NoSuchAlgorithmException {
    FileUtils.forceMkdirParent(exportPath.toFile());
    ZipOutputStream zos =
        new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(exportPath.toFile())));
    CipherUtils cipherUtils = new CipherUtils(exportPath);
    zos.close();
    cipherUtils.createZipChecksumFile();
  }
}
