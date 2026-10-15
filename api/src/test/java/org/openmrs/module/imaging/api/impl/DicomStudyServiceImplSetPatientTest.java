package org.openmrs.module.imaging.api.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonName;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.imaging.api.OverrideReasonRequiredException;
import org.openmrs.module.imaging.api.dao.DicomStudyDao;
import org.openmrs.module.imaging.api.match.MatchThresholds;
import org.openmrs.module.imaging.api.study.DicomStudy;

/**
 * Server-side enforcement of the match-confidence rule in
 * {@link DicomStudyServiceImpl#setPatient(DicomStudy, Patient, String)}, without a database or a
 * Spring context: the thresholds lookup is overridden and the DAO only records what it is asked to
 * save.
 */
public class DicomStudyServiceImplSetPatientTest {
	
	private final List<DicomStudy> saved = new ArrayList<DicomStudy>();
	
	private final List<String> logLines = new ArrayList<String>();
	
	private final AppenderSkeleton appender = new AppenderSkeleton() {
		
		@Override
		protected void append(LoggingEvent event) {
			logLines.add(event.getRenderedMessage());
		}
		
		@Override
		public void close() {
		}
		
		@Override
		public boolean requiresLayout() {
			return false;
		}
	};
	
	private final Logger moduleLogger = Logger.getLogger("org.openmrs.module.imaging");
	
	private DicomStudyServiceImpl service;
	
	@Before
	public void setUp() {
		Context.setUserContext(new UserContext());
		// openmrs-api's test log4j.xml sets the root logger to ERROR
		moduleLogger.setLevel(Level.INFO);
		moduleLogger.addAppender(appender);
		service = new DicomStudyServiceImpl() {
			
			@Override
			protected MatchThresholds getMatchThresholds() {
				return MatchThresholds.defaults();
			}
		};
		service.setDao(new DicomStudyDao() {
			
			@Override
			public void save(DicomStudy study) {
				saved.add(study);
			}
		});
	}
	
	@After
	public void tearDown() {
		moduleLogger.removeAppender(appender);
		moduleLogger.setLevel(null);
		Context.clearUserContext();
	}
	
	private static Patient patient(int id, String given, String family, String identifier) {
		Patient patient = new Patient(id);
		patient.addName(new PersonName(given, null, family));
		if (identifier != null) {
			PatientIdentifier pi = new PatientIdentifier();
			pi.setIdentifier(identifier);
			pi.setPreferred(true);
			patient.addIdentifier(pi);
		}
		return patient;
	}
	
	private static DicomStudy study(String dicomPatientName, String dicomPatientId) {
		return new DicomStudy("1.2.3", "orthanc-1", null, null, dicomPatientName, "20260101", "", "", "", dicomPatientId, "");
	}
	
	private boolean logged(String... fragments) {
		for (String line : logLines) {
			boolean all = true;
			for (String fragment : fragments) {
				all &= line.contains(fragment);
			}
			if (all) {
				return true;
			}
		}
		return false;
	}
	
	@Test
	public void lowConfidenceWithoutReason_shouldBeRejectedAndNothingSaved() {
		Patient patient = patient(1, "Amina", "Benali", null);
		DicomStudy study = study("Karim Haddad", "");
		for (String reason : new String[] { null, "", "   \n " }) {
			try {
				service.setPatient(study, patient, reason);
				fail("expected OverrideReasonRequiredException for reason [" + reason + "]");
			}
			catch (OverrideReasonRequiredException e) {
				assertTrue(e.getScore() < 75);
			}
		}
		assertNull(study.getMrsPatient());
		assertTrue(saved.isEmpty());
	}
	
	@Test
	public void twoArgumentSetPatient_shouldNotBypassTheRule() {
		DicomStudy study = study("Karim Haddad", "");
		try {
			service.setPatient(study, patient(1, "Amina", "Benali", null));
			fail("expected OverrideReasonRequiredException");
		}
		catch (OverrideReasonRequiredException expected) {}
		assertNull(study.getMrsPatient());
		assertTrue(saved.isEmpty());
	}
	
	@Test
	public void lowConfidenceWithReason_shouldBeSavedAndTheReasonLogged() {
		Patient patient = patient(1, "Amina", "Benali", null);
		DicomStudy study = study("Karim Haddad", "");
		service.setPatient(study, patient, "Name changed\nafter marriage");
		assertSame(patient, study.getMrsPatient());
		assertEquals(1, saved.size());
		assertTrue(logged("Low-confidence link", "1.2.3", "patient 1", "Reason: Name changed after marriage"));
	}
	
	@Test
	public void highConfidenceWithoutReason_shouldBeSaved() {
		Patient patient = patient(1, "Amina", "Benali", null);
		DicomStudy study = study("BENALI^AMINA", "");
		service.setPatient(study, patient, null);
		assertSame(patient, study.getMrsPatient());
		assertEquals(1, saved.size());
	}
	
	@Test
	public void identifierMatch_shouldNeedNoReasonEvenWithADifferentName() {
		Patient patient = patient(1, "Amina", "Benali", "CHU-0042");
		DicomStudy study = study("Someone Else", "CHU-0042");
		service.setPatient(study, patient, null);
		assertSame(patient, study.getMrsPatient());
	}
	
	@Test
	public void unlinking_shouldNeedNoReason() {
		Patient patient = patient(1, "Amina", "Benali", null);
		DicomStudy study = study("Karim Haddad", "");
		study.setMrsPatient(patient);
		service.setPatient(study, null, null);
		assertNull(study.getMrsPatient());
		assertEquals(1, saved.size());
	}
	
	@Test
	public void movingToAnotherPatient_shouldAlwaysBeLoggedWithBothPatients() {
		Patient first = patient(7, "Amina", "Benali", null);
		Patient second = patient(8, "Amina", "Benali", null);
		DicomStudy study = study("Amina Benali", "");
		study.setMrsPatient(first);
		service.setPatient(study, second, null); // 100% name match: no reason needed, still logged
		assertSame(second, study.getMrsPatient());
		assertTrue(logged("moved from patient 7 to patient 8"));
	}
	
	@Test
	public void relinkingToTheSamePatient_shouldNotBeLoggedAsAMove() {
		Patient patient = patient(7, "Amina", "Benali", null);
		DicomStudy study = study("Amina Benali", "");
		study.setMrsPatient(patient);
		service.setPatient(study, patient, null);
		for (String line : logLines) {
			assertTrue(line, !line.contains("moved from"));
		}
	}
}
