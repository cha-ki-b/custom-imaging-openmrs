<%
    ui.decorateWith("appui", "standardEmrPage",  [ title: ui.message("imaging.app.studySeries.title") ])
    ui.includeCss("imaging", "general.css")
    ui.includeCss("imaging", "series.css")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.escapeJs(ui.encodeHtmlContent(ui.format(patient.familyName))) }, ${ ui.escapeJs(ui.encodeHtmlContent(ui.format(patient.givenName))) }",
            link: '${ui.pageLink("coreapps", "clinicianfacing/patient", [patientId: patient.id])}'},
        { label: "${ ui.message("imaging.studies") }",
            link: '${ui.pageLink("imaging", "studies", [patientId: patient.id])}'},
        { label: "${ ui.message("imaging.series") }" }
    ];
</script>


${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient ]) }
${ ui.includeFragment("uicommons", "infoAndErrorMessage")}
<% ui.includeJavascript("imaging", "sortable.min.js") %>
<% ui.includeJavascript("imaging", "filter_table.js")%>

<h2>
    ${ ui.message("imaging.series") }
</h2>

<div style="color:red;">
${param["message"]?.getAt(0) ?: ""}
</div>

<script>
    function togglePopupDeleteSeries(orthancSeriesUID, studyId, patient) {
        const overlay = document.getElementById('popupOverlayDeleteSeries');
        overlay.classList.toggle('show');
        document.deleteSeriesForm.action = "/${contextPath}/module/imaging/deleteSeries.form?orthancSeriesUID="
                                             + orthancSeriesUID
                                             + "&studyId=" + studyId
                                             + "&patientId=" + patient;
    }
</script>

<div id="table-scroll">
    <table id="series" class="table table-sm table-responsive-sm table-responsive-md table-responsive-lg table-responsive-xl" data-sortable>
        <thead class="imaging-table-thead">
            <script src="filter_table.js" defer></script>
            <tr>
                <th>${ ui.message("imaging.app.seriesInstanceUID.label")}</th>
                <th>${ ui.message("imaging.app.seriesNumber.label")}</th>
                <th>${ ui.message("imaging.app.description.label")}</th>
                <th>${ ui.message("imaging.app.date.label")}</th>
                <th>${ ui.message("imaging.app.modality.label")}</th>
                <th  data-no-filter style="width: 70px;">${ ui.message("coreapps.actions") }</th>
            </tr>
        </thead>
        <tbody>
            <% if (serieses.size() == 0) { %>
                <tr>
                    <td colspan="6" align="center">${ui.message("imaging.series.none")}</td>
                </tr>
            <% } %>
            <% serieses.each { series ->
                def baseUrl = series.orthancConfiguration.orthancProxyUrl?.trim() ? series.orthancConfiguration.orthancProxyUrl : series.orthancConfiguration.orthancBaseUrl
                def normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl[0..-2] : baseUrl
            %>
                <tr>
                    <td class="uid-td">
                        <a href="${ui.pageLink("imaging", "instances", [patientId: patient.id, seriesInstanceUID: series.seriesInstanceUID, studyId: studyId])}">${ui.format(series.seriesInstanceUID)}</a>
                    </td>
                    <td>${ui.format(series.seriesNumber)}</td>
                    <td>${ui.format(series.seriesDescription)}</td>
                    <td>${ui.format(series.seriesDate)}</td>
                    <td>${ui.format(series.modality)}</td>
                     <td>
                         <% if (privilegeModifyImageData) { %>
                            <a class="delete-series"
                                onclick="togglePopupDeleteSeries('${series.orthancSeriesUID}', '${studyId}', '${patient.id}')"><i class="icon-remove" delete-action></i>
                            </a>
                        <% } %>
                        <% if (ui.format(series.modality) != "RTDOSE"  && ui.format(series.modality) != "RTSTRUCT") { %>
                            <a href="${normalizedBaseUrl}/stone-webviewer/index.html?study=${studyInstanceUID}&series=${series.seriesInstanceUID}"
                                title="${ ui.message("imaging.app.openStoneView.label") }">
                                <img class="series-stone-img" src="${ ui.resourceLink("imaging", "images/stoneViewer.png") }"/></a>
                        <% } %>
                        <a href="${normalizedBaseUrl}/ui/app/#/filtered-studies?StudyInstanceUID=${studyInstanceUID}&expand=series" title="${ ui.message("imaging.app.orthancExplorer.label") }">
                            <img class="orthanc-img" alt="Show image data in Orthanc explorer" src="${ ui.resourceLink("imaging", "images/orthanc.png")}"/></a>
                        <%
                            // medreport hook: hands this row's DICOM metadata to medreport's
                            // editor. The button only appears when medreport is running, and
                            // medreport itself decides (server-side) whether this user may
                            // actually write a report - this page makes no such judgement.
                            if (medreportAvailable) {
                        %>
                            <a href="javascript:void(0)" title="${ ui.message("imaging.app.writeReport.label") }"
                               onclick="medreportImaging.openForSeries({
                                   seriesUid: '${ ui.escapeJs(series.orthancSeriesUID) }',
                                   studyInstanceUid: '${ ui.escapeJs(studyInstanceUID) }',
                                   modality: '${ ui.escapeJs(ui.format(series.modality)) }',
                                   studyDate: '${ ui.escapeJs(ui.format(series.seriesDate)) }',
                                   studyDescription: '${ ui.escapeJs(ui.format(series.seriesDescription)) }' })">
                                <i class="icon-file-alt"></i></a>
                        <% } %>
                     </td>
                </tr>
            <% } %>
        </tbody>
    </table>
</div>
<br/>

<%
    // ---------------------------------------------------------------------------------
    // medreport integration - the whole of it.
    //
    // This page passes the study identifiers it already displays; medreport's fragment
    // renders the report list, the editor, the permission-driven actions and the version
    // history, and talks to medreport's own REST resources. No report logic lives in the
    // imaging module, and the guard means this page is unchanged when medreport is absent.
    // ---------------------------------------------------------------------------------
    if (medreportAvailable) {
        // Offer the whole study plus each of its series, so a report can cover the study as
        // a whole or just the sequences the clinician actually read.
        def medreportImages = [[
                studyUid         : orthancStudyUID,
                studyInstanceUid : studyInstanceUID,
                studyDate        : studyDate,
                studyDescription : studyDescription ]]
        serieses.each { series ->
            medreportImages << [
                studyUid         : orthancStudyUID,
                seriesUid        : series.orthancSeriesUID,
                studyInstanceUid : studyInstanceUID,
                modality         : series.modality,
                studyDate        : series.seriesDate,
                studyDescription : series.seriesDescription ]
        }
%>
    ${ ui.includeFragment("medreport", "imageReports", [
            patientId       : patient.id,
            studyUid        : orthancStudyUID,
            availableImages : medreportImages ]) }
<% } %>

<div id="popupOverlayDeleteSeries" class="overlay-container">
    <div class="popup-box" style="width: 65%;">
        <h2>Delete Series</h2>
        <form name="deleteSeriesForm" class="form-container" method='POST'>
            <h3 id="deleteSeriesMessage">${ ui.message("imaging.deleteSeries.message") }</h3>
            <div class="popup-box-btn">
                <button class="btn-submit" type="submit">${ ui.message("imaging.action.delete") }</button>
                <button class="btn-close-popup" type="button" onclick="togglePopupDeleteSeries()">Cancel</button>
            </div>
        </form>
    </div>
</div>