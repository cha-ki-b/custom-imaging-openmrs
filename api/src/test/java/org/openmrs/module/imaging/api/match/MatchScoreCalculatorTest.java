package org.openmrs.module.imaging.api.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class MatchScoreCalculatorTest {
	
	private static final MatchThresholds DEFAULTS = MatchThresholds.defaults();
	
	private static Map<String, Integer> scores(int... values) {
		Map<String, Integer> map = new LinkedHashMap<String, Integer>();
		for (int i = 0; i < values.length; i++) {
			map.put("s" + i, values[i]);
		}
		return map;
	}
	
	@Test
	public void classify_shouldNotMarkAPoorBestMatchAsHighest() {
		// The case reported in testing: a 34% match displayed as "Highest".
		Map<String, MatchTier> tiers = MatchScoreCalculator.classify(scores(34, 20), DEFAULTS, 90);
		assertEquals(MatchTier.LOW, tiers.get("s0"));
		assertEquals(MatchTier.VERY_LOW, tiers.get("s1"));
	}
	
	@Test
	public void classify_shouldMarkBestMatchAsHighestWhenItReachesTheMinimum() {
		Map<String, MatchTier> tiers = MatchScoreCalculator.classify(scores(96, 80), DEFAULTS, 90);
		assertEquals(MatchTier.HIGHEST, tiers.get("s0"));
		assertEquals(MatchTier.HIGH, tiers.get("s1"));
	}
	
	@Test
	public void classify_shouldApplyTheMinimumInclusively() {
		assertEquals(MatchTier.HIGHEST, MatchScoreCalculator.classify(scores(90), DEFAULTS, 90).get("s0"));
		assertEquals(MatchTier.HIGH, MatchScoreCalculator.classify(scores(89), DEFAULTS, 90).get("s0"));
	}
	
	@Test
	public void classify_shouldMarkEveryTiedBestMatchAsHighest() {
		Map<String, MatchTier> tiers = MatchScoreCalculator.classify(scores(100, 100, 40), DEFAULTS, 90);
		assertEquals(MatchTier.HIGHEST, tiers.get("s0"));
		assertEquals(MatchTier.HIGHEST, tiers.get("s1"));
		assertEquals(MatchTier.LOW, tiers.get("s2"));
	}
	
	@Test
	public void classify_minimumShouldBeIndependentOfTheVeryHighThreshold() {
		// above "very high" (95): a 97 is Very High but not Highest
		assertEquals(MatchTier.VERY_HIGH, MatchScoreCalculator.classify(scores(97), DEFAULTS, 98).get("s0"));
		// below "high" (75): a 70 can still be Highest
		assertEquals(MatchTier.HIGHEST, MatchScoreCalculator.classify(scores(70, 10), DEFAULTS, 60).get("s0"));
	}
	
	@Test
	public void classify_shouldKeepTheAllZeroGuardEvenWithAZeroMinimum() {
		Map<String, MatchTier> tiers = MatchScoreCalculator.classify(scores(0, 0), DEFAULTS, 0);
		assertEquals(MatchTier.VERY_LOW, tiers.get("s0"));
		assertEquals(MatchTier.VERY_LOW, tiers.get("s1"));
	}
	
	@Test
	public void requiresOverrideReason_shouldBeTrueBelowTheHighThresholdOnly() {
		assertFalse(MatchScoreCalculator.requiresOverrideReason(100, DEFAULTS));
		assertFalse(MatchScoreCalculator.requiresOverrideReason(95, DEFAULTS));
		assertFalse(MatchScoreCalculator.requiresOverrideReason(75, DEFAULTS));
		assertTrue(MatchScoreCalculator.requiresOverrideReason(74, DEFAULTS));
		assertTrue(MatchScoreCalculator.requiresOverrideReason(50, DEFAULTS));
		assertTrue(MatchScoreCalculator.requiresOverrideReason(25, DEFAULTS));
		assertTrue(MatchScoreCalculator.requiresOverrideReason(0, DEFAULTS));
	}
	
	@Test
	public void requiresOverrideReason_shouldFollowConfiguredThresholds() {
		MatchThresholds custom = new MatchThresholds(90, 60, 40, 20);
		assertFalse(MatchScoreCalculator.requiresOverrideReason(60, custom));
		assertTrue(MatchScoreCalculator.requiresOverrideReason(59, custom));
	}
}
