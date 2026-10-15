/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.imaging.page.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.ImagingConstants;
import org.openmrs.module.imaging.ImagingProperties;
import org.openmrs.module.imaging.api.DicomStudyService;
import org.openmrs.module.imaging.api.OverrideReasonRequiredException;
import org.openmrs.module.imaging.api.match.MatchScoreCalculator;
import org.openmrs.module.imaging.api.match.MatchThresholds;
import org.openmrs.module.imaging.api.match.MatchTier;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.ui.framework.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class SyncStudiesPageController {
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	/**
	 * @param message optional status message key. Optional because the "show all" / "hide very low"
	 *            links reach this page directly, without the redirect that supplies it; it is only
	 *            read by the template.
	 * @param showAll true to also list Very Low matches, which are hidden by default
	 */
	public void get(Model model, @RequestParam(value = "patientId") Patient patient,
	        @RequestParam(value = "message", required = false, defaultValue = "") String message,
	        @RequestParam(value = "showAll", required = false, defaultValue = "false") boolean showAll) {
		ImagingProperties imageProps = Context.getRegisteredComponent("imagingProperties", ImagingProperties.class);
		DicomStudyService dicomStudyService = Context.getService(DicomStudyService.class);
		List<DicomStudy> allStudies = dicomStudyService.getAllStudies();
		
		final HashMap<String, Integer> match = new HashMap<String, Integer>();
		for (DicomStudy study : allStudies) {
			// Combines fuzzy name similarity with any available DICOM PatientID/PatientBirthDate
			// signals - see DicomStudyServiceImpl#computeMatchScore.
			int score = dicomStudyService.computeMatchScore(patient, study);
			match.put(study.getStudyInstanceUID(), score);
		}
		model.addAttribute("match", match);
		
		MatchThresholds thresholds = imageProps.getMatchThresholds();
		Map<String, MatchTier> matchTier = MatchScoreCalculator.classify(match, thresholds,
		    imageProps.getHighestMinimumScore());
		model.addAttribute("matchTier", matchTier);
		
		// Best match first. Very Low matches are hidden unless asked for, except a study already
		// linked to this patient, which must stay visible so the link can be removed.
		List<DicomStudy> studies = new ArrayList<DicomStudy>();
		Map<String, Boolean> reasonRequired = new HashMap<String, Boolean>();
		int hiddenCount = 0;
		for (DicomStudy study : allStudies) {
			int score = match.get(study.getStudyInstanceUID());
			boolean linkedHere = study.getMrsPatient() != null && study.getMrsPatient().getId().equals(patient.getId());
			if (!showAll && !linkedHere && MatchScoreCalculator.tierForScore(score, thresholds) == MatchTier.VERY_LOW) {
				hiddenCount++;
				continue;
			}
			studies.add(study);
			reasonRequired.put(study.getStudyInstanceUID(), MatchScoreCalculator.requiresOverrideReason(score, thresholds));
		}
		Collections.sort(studies, new Comparator<DicomStudy>() {
			
			@Override
			public int compare(DicomStudy a, DicomStudy b) {
				return match.get(b.getStudyInstanceUID()).compareTo(match.get(a.getStudyInstanceUID()));
			}
		});
		model.addAttribute("studies", studies);
		model.addAttribute("reasonRequired", reasonRequired);
		model.addAttribute("showAll", showAll);
		model.addAttribute("hiddenCount", hiddenCount);
		
		model.addAttribute("privilegeModifyImageData",
		    Context.getAuthenticatedUser().hasPrivilege(ImagingConstants.PRIVILEGE_MODIFY_IMAGE_DATA));
		model.addAttribute("ohifBaseUrl", imageProps.getOhifBaseUrl());
		model.addAttribute("ohifAiBaseUrl", imageProps.getOhifAiBaseUrl());
		model.addAttribute("privilegeUseOhifAi",
		    Context.getAuthenticatedUser().hasPrivilege(ImagingConstants.PRIVILEGE_USE_OHIF_AI));
	}
	
	/**
	 * @param redirectAttributes the redirect attributes
	 * @param patient the openmrs patient
	 * @param studyId the study ID
	 * @param isChecked is the study assigned?
	 * @param overrideReason typed justification, required by the service for a Medium/Low/Very Low
	 *            match
	 * @param showAll carried back to the page so the list stays as the user left it
	 * @return the redirect url
	 */
	@RequestMapping(value = "/module/imaging/assignStudy.form", method = RequestMethod.POST)
	public String assignStudy(RedirectAttributes redirectAttributes, @RequestParam(value = "patientId") Patient patient,
	        @RequestParam(value = "studyId") int studyId, boolean isChecked,
	        @RequestParam(value = "overrideReason", required = false) String overrideReason,
	        @RequestParam(value = "showAll", required = false, defaultValue = "false") boolean showAll) {
		DicomStudyService dicomStudyService = Context.getService(DicomStudyService.class);
		String message;
		String messageType = "success";
		try {
			if (isChecked) {
				dicomStudyService.setPatient(dicomStudyService.getDicomStudy(studyId), patient, overrideReason);
				message = "imaging.study.assign.success";
			} else {
				dicomStudyService.setPatient(dicomStudyService.getDicomStudy(studyId), null);
				message = "imaging.study.unassign.success";
			}
		}
		catch (OverrideReasonRequiredException e) {
			message = "imaging.study.assign.reasonRequired";
			messageType = "error";
			redirectAttributes.addAttribute("messageArg0", e.getScore());
		}
		
		redirectAttributes.addAttribute("patientId", patient.getId());
		redirectAttributes.addAttribute("message", message);
		redirectAttributes.addAttribute("messageType", messageType);
		if (showAll) {
			redirectAttributes.addAttribute("showAll", true);
		}
		return "redirect:/imaging/syncStudies.page";
	}
}
