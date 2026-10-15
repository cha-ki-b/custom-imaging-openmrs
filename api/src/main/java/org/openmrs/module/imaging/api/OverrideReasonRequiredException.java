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
package org.openmrs.module.imaging.api;

import org.openmrs.api.APIException;

/**
 * Thrown by
 * {@link DicomStudyService#setPatient(org.openmrs.module.imaging.api.study.DicomStudy, org.openmrs.Patient, String)}
 * when a study would be linked to a patient with a Medium, Low or Very Low match score and no
 * override reason was given. Nothing is saved when this is thrown.
 */
public class OverrideReasonRequiredException extends APIException {
	
	private static final long serialVersionUID = 1L;
	
	private final int score;
	
	public OverrideReasonRequiredException(int score) {
		super("A typed reason is required to link a study with a match score of " + score + "%");
		this.score = score;
	}
	
	/**
	 * @return the match score that triggered the requirement
	 */
	public int getScore() {
		return score;
	}
}
