<%
    ui.decorateWith("appui", "standardEmrPage",  [ title: ui.message("imaging.app.imageStudies.title") ])
    ui.includeCss("imaging", "general.css")
%>

<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.escapeJs(ui.encodeHtmlContent(ui.format(patient.familyName))) }, ${ ui.escapeJs(ui.encodeHtmlContent(ui.format(patient.givenName))) }",
            link: '${ui.pageLink("coreapps", "clinicianfacing/patient", [patientId: patient.id])}'},
        { label: "${ ui.message("imaging.studies") }", link: '${ui.pageLink("imaging", "studies", [patientId: patient.id])}'},
        { label: "${ ui.message("imaging.study.synchronization") }" }
    ];
</script>

<% ui.includeJavascript("imaging", "sortable.min.js") %>
<% ui.includeJavascript("imaging", "filter_table.js")%>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient ]) }
${ ui.includeFragment("uicommons", "infoAndErrorMessage")}

<h2>
    ${ ui.message("imaging.studies.all") }
</h2>

<%
    def statusMessageKey = param["message"]?.getAt(0)
    def statusMessageType = param["messageType"]?.getAt(0)
    def statusMessageArgs = []
    if (param["messageArg0"]) { statusMessageArgs << param["messageArg0"].getAt(0) }
    if (param["messageArg1"]) { statusMessageArgs << param["messageArg1"].getAt(0) }
    /* Spread (*) the list: passed as-is, Groovy hands it to the varargs as ONE argument and
       {0} renders as "[3, 5]" while {1} stays literal. */
    def statusMessageText = statusMessageKey ? (statusMessageArgs ? ui.message(statusMessageKey, *statusMessageArgs) : ui.message(statusMessageKey)) : ""
    def statusMessageColor = statusMessageType == "success" ? "green" : "red"
    def viewedPatientName = ui.format(patient.givenName) + " " + ui.format(patient.familyName)
    def viewedPatientIdentifier = patient.patientIdentifier?.identifier
%>
<% if (statusMessageText) { %>
<div style="color:${statusMessageColor};">
${statusMessageText}
</div>
<% } %>

<% /* Very Low matches are hidden by default; these links reach the page without a "message"
      parameter, which is why the controller declares it optional. */ %>
<% if (showAll) { %>
<div class="imaging-match-filter">
    ${ ui.message("imaging.sync.showingAll") }
    <a href="${ ui.pageLink("imaging", "syncStudies", [patientId: patient.id]) }">${ ui.message("imaging.sync.hideVeryLow") }</a>
</div>
<% } else if (hiddenCount > 0) { %>
<div class="imaging-match-filter">
    ${ ui.message("imaging.sync.hiddenVeryLow", hiddenCount) }
    <a href="${ ui.pageLink("imaging", "syncStudies", [patientId: patient.id, showAll: true]) }">${ ui.message("imaging.sync.showAll") }</a>
</div>
<% } %>

<div id="table-scroll">
    <table id="sync-studies" class="table table-sm table-responsive-sm table-responsive-md table-responsive-lg table-responsive-xl" data-sortable>
        <thead class="imaging-table-thead">
            <script src="filter_table.js" defer></script>
            <tr>
                <th style="width: 3px;"></th>
                <th>Match</th>
                <th>${ ui.message("imaging.app.matchConfidence.label")}</th>
                <% /* Tooltip on an inner span: filter_table.js gives no filter box to a <th> that has any attribute. */ %>
                <th><span title="${ ui.encodeHtmlAttribute(ui.message("imaging.sync.linkedTo.tooltip")) }" style="cursor:help;">${ ui.message("imaging.sync.linkedTo.label") } &#9432;</span></th>
                <th>${ ui.message("imaging.app.studyInstanceUid.label")}</th>
                <th>${ ui.message("imaging.app.patientName.label")}</th>
                <th>${ ui.message("imaging.app.date.label")}</th>
                <th>${ ui.message("imaging.app.description.label")}</th>
                <th>${ ui.message("imaging.app.server.label")}</th>
                <th data-no-filter style="width: 85px;">${ ui.message("coreapps.actions") }</th>
            </tr>
        </thead>
        <tbody>
            <% if (studies.size() == 0) { %>
                <tr>
                    <td colspan="10" align="center">${ui.message("imaging.studies.none")}</td>
                </tr>
            <% } %>
            <% studies.each { study ->
                def baseUrl = study.orthancConfiguration.orthancProxyUrl?.trim() ? study.orthancConfiguration.orthancProxyUrl : study.orthancConfiguration.orthancBaseUrl
                def normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl[0..-2] : baseUrl
                def tier = matchTier[study.studyInstanceUID]
                def linkedHere = study.mrsPatient != null && study.mrsPatient.id == patient.id
                def linkedOther = study.mrsPatient != null && !linkedHere
            %>
                <tr>
                    <td>
                        <input type="checkbox" ${linkedHere ? "checked" : ""}
                                data-study-id="${study.id}"
                                data-reason-required="${reasonRequired[study.studyInstanceUID] ? "true" : "false"}"
                                data-linked-other="${linkedOther ? "true" : "false"}"
                                onchange="imagingOpenAssignDialog(this)"/>
                    </td>
                    <td data-role="score">${match[study.studyInstanceUID]}%</td>
                    <td data-role="tier">
                        <span style="display:inline-block; padding:2px 8px; border-radius:4px; color:#fff; font-size:0.85em; white-space:nowrap; background-color:${tier.colorHex};">${ui.message(tier.messageKey)}</span>
                    </td>
                    <td>
                        <% if (study.mrsPatient == null) { %>
                        <span style="color:#6c757d; font-style:italic;">${ ui.message("imaging.sync.linkedTo.none") }</span>
                        <% } else { %>
                        <span data-role="linked-name">${ ui.encodeHtmlContent(ui.format(study.mrsPatient.givenName) + " " + ui.format(study.mrsPatient.familyName)) }<% if (study.mrsPatient.patientIdentifier) { %> (${ ui.encodeHtmlContent(study.mrsPatient.patientIdentifier.identifier) })<% } %></span>
                        <% if (linkedHere) { %><strong>${ ui.message("imaging.sync.linkedTo.thisPatient") }</strong><% } %>
                        <% } %>
                    </td>
                    <td class="uid-td">${ui.format(study.studyInstanceUID)}</td>
                    <td data-role="dicom-name">${ui.format(study.patientName)}</td>
                    <td data-role="date">${ui.format(study.studyDate)}</td>
                    <td data-role="description">${ui.format(study.studyDescription)}</td>
                    <td>${ui.format(study.orthancConfiguration.orthancBaseUrl)}</td>
                     <td>
                        <a href="${normalizedBaseUrl}/stone-webviewer/index.html?study=${ui.format(study.studyInstanceUID)}" title="${ ui.message("imaging.app.openStoneView.label") }">
                            <img class="stone-img" alt="Show image in stone viewer" src="${ ui.resourceLink("imaging", "images/stoneViewer.png")}"/></a>
                        <% if (ohifBaseUrl?.trim()) {
                            def normalizedOhifBaseUrl = ohifBaseUrl.endsWith("/") ? ohifBaseUrl[0..-2] : ohifBaseUrl
                        %>
                        <a href="${normalizedOhifBaseUrl}/viewer?StudyInstanceUIDs=${ui.format(study.studyInstanceUID)}" title="${ ui.message("imaging.app.openOHIFView.label") }" target="_blank">
                            <img class="ohif-img" alt="Show image in OHIF viewer" src="${ ui.resourceLink("imaging", "images/ohifViewer.png")}"/></a>
                        <% } %>
                        <% /* OHIF-AI: a second, independent viewer. Hidden unless the URL is set AND
                              the user holds the privilege - its output reaches the PACS. */ %>
                        <% if (privilegeUseOhifAi && ohifAiBaseUrl?.trim()) {
                            def normalizedOhifAiBaseUrl = ohifAiBaseUrl.endsWith("/") ? ohifAiBaseUrl[0..-2] : ohifAiBaseUrl
                        %>
                        <a href="${normalizedOhifAiBaseUrl}/viewer?StudyInstanceUIDs=${ui.format(study.studyInstanceUID)}" title="${ ui.message("imaging.app.openOhifAiView.label") }" target="_blank">
                            <img class="ohif-ai-img" alt="Show image in the AI viewer" src="${ ui.resourceLink("imaging", "images/ohifAiViewer.png")}"/></a>
                        <% } %>
                        <a href="${normalizedBaseUrl}/ui/app/#/filtered-studies?StudyInstanceUID=${ui.format(study.studyInstanceUID)}&expand=study" title="${ ui.message("imaging.app.orthancExplorer.label") }">
                            <img class="orthanc-img" alt="Show image data in Orthanc explorer" src="${ ui.resourceLink("imaging", "images/orthanc.png")}"/></a>
                    </td>
                </tr>
            <% } %>
        </tbody>
    </table>
</div>
<br/>

<% /* Confirmation dialog. Ticking or unticking a checkbox only opens it; nothing is sent until
      "Confirm". The typed-reason rule is also enforced server-side in DicomStudyService. */ %>
<style>
    #assign-study-dialog { display:none; position:fixed; top:0; left:0; right:0; bottom:0; background:rgba(0,0,0,0.5); z-index:1000; align-items:center; justify-content:center; }
    #assign-study-dialog .dialog-box { background:#fff; max-width:600px; width:90%; padding:1em 1.5em; border-radius:6px; box-shadow:0 4px 16px rgba(0,0,0,0.3); }
    #assign-study-dialog .dialog-warning { background:#fff3cd; border:1px solid #ffc107; padding:0.5em; margin:0.5em 0; }
    #assign-study-dialog textarea { width:100%; min-height:4em; }
    #assign-study-dialog .dialog-buttons { text-align:right; margin-top:1em; }
</style>
<div id="assign-study-dialog" role="dialog" aria-modal="true">
    <div class="dialog-box">
        <h3 data-show="assign">${ ui.message("imaging.sync.confirm.assign.title") }</h3>
        <h3 data-show="unassign">${ ui.message("imaging.sync.confirm.unassign.title") }</h3>
        <p>
            <span data-show="assign">${ ui.message("imaging.sync.confirm.assign.target") }</span>
            <span data-show="unassign">${ ui.message("imaging.sync.confirm.unassign.target") }</span>
            <br/><strong>${ ui.encodeHtmlContent(viewedPatientName) }<% if (viewedPatientIdentifier) { %> (${ ui.encodeHtmlContent(viewedPatientIdentifier) })<% } %></strong>
        </p>
        <p>
            ${ ui.message("imaging.sync.confirm.study") }: <span data-fill="dicom-name"></span>, <span data-fill="date"></span>, <span data-fill="description"></span><br/>
            ${ ui.message("imaging.sync.confirm.match") }: <strong><span data-fill="score"></span> - <span data-fill="tier"></span></strong>
        </p>
        <div class="dialog-warning" data-show="other">
            ${ ui.message("imaging.sync.confirm.otherPatient") } <strong><span data-fill="linked-name"></span></strong>.
            ${ ui.message("imaging.sync.confirm.otherPatient.move") }
        </div>
        <form id="assign-study-form" method="POST" action="/${contextPath}/module/imaging/assignStudy.form">
            <input type="hidden" name="patientId" value="${patient.id}"/>
            <input type="hidden" name="studyId" value=""/>
            <input type="hidden" name="isChecked" value=""/>
            <% if (showAll) { %><input type="hidden" name="showAll" value="true"/><% } %>
            <div data-show="reason">
                <label for="assign-study-reason">${ ui.message("imaging.sync.confirm.reason.label") }</label>
                <textarea id="assign-study-reason" name="overrideReason" maxlength="500"></textarea>
            </div>
            <div class="dialog-buttons">
                <button type="button" class="cancel" onclick="imagingCloseAssignDialog()">${ ui.message("imaging.sync.confirm.cancel") }</button>
                <button type="submit" class="confirm" id="assign-study-confirm">${ ui.message("imaging.sync.confirm.confirm") }</button>
            </div>
        </form>
    </div>
</div>

<script type="text/javascript">
    function imagingOpenAssignDialog(checkbox) {
        var assign = checkbox.checked;
        checkbox.checked = !assign; // the link only changes once the dialog is confirmed
        var row = checkbox.closest("tr");
        var dialog = document.getElementById("assign-study-dialog");
        var reasonRequired = assign && checkbox.getAttribute("data-reason-required") === "true";
        var shown = { assign: assign, unassign: !assign, reason: reasonRequired,
            other: assign && checkbox.getAttribute("data-linked-other") === "true" };
        Array.prototype.forEach.call(dialog.querySelectorAll("[data-show]"), function (el) {
            el.style.display = shown[el.getAttribute("data-show")] ? "" : "none";
        });
        Array.prototype.forEach.call(dialog.querySelectorAll("[data-fill]"), function (el) {
            var source = row.querySelector("[data-role='" + el.getAttribute("data-fill") + "']");
            el.textContent = source ? source.textContent.trim() : "";
        });
        var form = document.getElementById("assign-study-form");
        form.elements["studyId"].value = checkbox.getAttribute("data-study-id");
        form.elements["isChecked"].value = assign ? "true" : "false";
        var reason = document.getElementById("assign-study-reason");
        reason.value = "";
        reason.required = reasonRequired;
        reason.oninput = function () {
            document.getElementById("assign-study-confirm").disabled = reasonRequired && !reason.value.trim();
        };
        reason.oninput();
        dialog.style.display = "flex";
    }

    function imagingCloseAssignDialog() {
        document.getElementById("assign-study-dialog").style.display = "none";
    }

    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") { imagingCloseAssignDialog(); }
    });
</script>
