package com.shootoff.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestHitFactor {
	@Test
	void pointsDividedByTimeWhenClean() {
		// The owner's real run: 10 shots, 95 points, 18.30 s, no misses
		assertEquals(95 / 18.30, HitFactor.compute(95, 0, 0, 18.30), 1e-9);
	}

	@Test
	void eachMissAndParMissCostsTenPoints() {
		assertEquals((80 - 10 - 10) / 20.0, HitFactor.compute(80, 1, 1, 20.0), 1e-9);
	}

	@Test
	void penaltiesNeverMakeTheScoreNegative() {
		assertEquals(0.0, HitFactor.compute(15, 2, 3, 30.0), 0.0);
	}

	@Test
	void zeroTimeScoresZero() {
		assertEquals(0.0, HitFactor.compute(50, 0, 0, 0.0), 0.0);
	}
}
