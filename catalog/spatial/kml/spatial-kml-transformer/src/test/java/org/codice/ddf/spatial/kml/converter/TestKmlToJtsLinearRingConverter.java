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
package org.codice.ddf.spatial.kml.converter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import de.micromata.opengis.kml.v_2_2_0.Kml;
import de.micromata.opengis.kml.v_2_2_0.LinearRing;
import de.micromata.opengis.kml.v_2_2_0.Placemark;
import java.io.InputStream;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestKmlToJtsLinearRingConverter {

  private static LinearRing testKmlLinearRing;

  @BeforeClass
  public static void setupClass() {
    InputStream stream =
        TestKmlToJtsLinearRingConverter.class.getResourceAsStream("/kmlLinearRing.kml");

    Kml kml = Kml.unmarshal(stream);

    testKmlLinearRing = ((LinearRing) ((Placemark) kml.getFeature()).getGeometry());
  }

  @Test
  public void testConversion() {
    com.vividsolutions.jts.geom.LinearRing jtsLinearRing =
        KmlToJtsLinearRingConverter.from(testKmlLinearRing);
    assertJtsLinearRing(jtsLinearRing);
  }

  @Test
  public void testNullKmlLinearRingReturnsNullJtsLinearRing() {
    com.vividsolutions.jts.geom.LinearRing jtsLinearRing = KmlToJtsLinearRingConverter.from(null);

    assertThat(jtsLinearRing, nullValue());
  }

  @Test
  public void testKmlLinearRingWithNoCoordinatesReturnsNull() {
    com.vividsolutions.jts.geom.LinearRing jtsLinearRing =
        KmlToJtsLinearRingConverter.from(new LinearRing());

    assertThat(jtsLinearRing, nullValue());
  }

  static void assertJtsLinearRing(
      LinearRing kmlLinearRing, com.vividsolutions.jts.geom.LinearRing jtsLinearRing) {
    assertThat(jtsLinearRing, notNullValue());
    TestKmlToJtsCoordinateConverter.assertJtsCoordinatesFromKmlCoordinates(
        kmlLinearRing.getCoordinates(), jtsLinearRing.getCoordinates());
  }

  private void assertJtsLinearRing(com.vividsolutions.jts.geom.LinearRing jtsLinearRing) {
    assertJtsLinearRing(testKmlLinearRing, jtsLinearRing);
  }
}
