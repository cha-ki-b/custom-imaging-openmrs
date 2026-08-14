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

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.imaging.ImagingConstants;
import org.openmrs.module.imaging.api.DicomStudyService;
import org.openmrs.module.imaging.api.study.DicomSeries;
import org.openmrs.module.imaging.api.study.DicomStudy;
import org.openmrs.ui.framework.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
public class SeriesPageController {
	
	protected Log log = LogFactory.getLog(this.getClass());
	
	public void get(Model model, @RequestParam(value = "studyId") int studyId) throws IOException {
		try {
			DicomStudyService dicomStudyService = Context.getService(DicomStudyService.class);
			DicomStudy dicomStudy = dicomStudyService.getDicomStudy(studyId);
			
			List<DicomSeries> seriesList = dicomStudyService.fetchSeries(dicomStudy);
			if (seriesList.isEmpty()) {
				seriesList = dicomStudyService.fetchSeries(dicomStudy);
			}
			model.addAttribute("serieses", seriesList);
			model.addAttribute("studyId", studyId);
			model.addAttribute("studyInstanceUID", dicomStudy.getStudyInstanceUID());
			model.addAttribute("privilegeModifyImageData",
			    Context.getAuthenticatedUser().hasPrivilege(ImagingConstants.PRIVILEGE_MODIFY_IMAGE_DATA));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * @param redirectAttributes the redirect attributes
	 * @param orthancSeriesUID the orthanc identifier UID for the series
	 * @param studyId the study ID
	 * @param patient the openmrs patient
	 * @return the delete status
	 */
	@RequestMapping(value = "/module/imaging/deleteSeries.form", method = RequestMethod.POST)
	public String deleteSeries(RedirectAttributes redirectAttributes,
	        @RequestParam(value = "orthancSeriesUID") String orthancSeriesUID, @RequestParam(value = "studyId") int studyId,
	        @RequestParam(value = "patientId") Patient patient) {
		
		String message;
		boolean hasPrivilege = Context.getAuthenticatedUser().hasPrivilege(ImagingConstants.PRIVILEGE_MODIFY_IMAGE_DATA);
		if (hasPrivilege) {
			DicomStudyService dicomStudyService = Context.getService(DicomStudyService.class);
			DicomStudy seriesStudy = dicomStudyService.getDicomStudy(studyId);
			try {
				dicomStudyService.deleteSeries(orthancSeriesUID, seriesStudy);
				message = "Series successfully deleted";
			}
			catch (IOException e) {
				message = "Deletion of series failed. Reason: " + e.getMessage();
			}
		} else {
			message = "Permission denied (you don't have the necessary privileges)";
		}
		
		redirectAttributes.addAttribute("patientId", patient.getId());
		redirectAttributes.addAttribute("studyId", studyId);
		redirectAttributes.addAttribute("message", message);
		return "redirect:/imaging/series.page";
	}
	
}
