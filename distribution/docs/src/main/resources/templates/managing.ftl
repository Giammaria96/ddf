<#list managingIntros as mi>
<#if (mi.title == "Managing Intro" && mi.status == "published")>
include::${mi.file}[]
</#if>
</#list>

<#list securingIntros?sort_by("order") as si>
<#if (si.status == "published")>
include::${si.file}[]
</#if>
</#list>

<#list securings?sort_by("order") as securing>
<#if (securing.status == "published")>

include::${securing.file}[leveloffset=+1]

<#list subSecurings?sort_by("order") as subSecuring>
<#if (subSecuring.parent == securing.title)>

include::${subSecuring.file}[leveloffset=+2]

</#if>
</#list>

</#if>
</#list>


<#list installingIntros?sort_by("order") as ii>
<#if (ii.status == "published")>
include::${ii.file}[]
</#if>
</#list>

<#-- installation steps have an 'order' property as they need to be performed in a specific order and documented as such. -->
<#list installings?sort_by("order") as installing>
<#if (installing.project == "${branding}" && installing.status == "published")>

include::${installing.file}[]

<#list subInstallings?sort_by("order") as subInstalling>
<#if (subInstalling.parent == installing.title)>

include::${subInstalling.file}[]

</#if>
</#list>

</#if>
</#list>

<#list managingSections?sort_by("order") as ms>
<#if (ms.status == "published")>

include::${ms.file}[]

</#if>
</#list>
<#include "configuring.ftl">

== Running

<#list runningIntros as ri>
<#if (ri.status == "published")>
include::${ri.file}[]
</#if>
</#list>

=== Starting

<#list startingIntros as si>
<#if (si.status == "published")>
include::${si.file}[]

</#if>
</#list>

=== Maintaining

<#list maintainings?sort_by("order") as maintaining>
<#if (maintaining.status == "published")>
include::${maintaining.file}[]

</#if>
</#list>

=== Monitoring

<#list monitorings?sort_by("order") as monitoring>
<#if (monitoring.status == "published")>
include::${monitoring.file}[]

</#if>
</#list>

=== Troubleshooting

<#list troubleshootings?sort_by("order") as troubleshooting>
<#if (troubleshooting.status == "published")>
include::${troubleshooting.file}[]

</#if>
</#list>

== Data Management

<#list dataManagements?sort_by("order") as dataManagement>
<#if (dataManagement.status == "published")>
include::${dataManagement.file}[]

</#if>
</#list>

