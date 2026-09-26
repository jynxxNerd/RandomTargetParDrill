package com.shootoff.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class TestHitFactorSummary {
	@Test
	void firstRunIsANewPersonalBest() {
		assertEquals("Hit Factor: 5.19   (10 rounds, 4.00 s par)\nNew personal best!",
				HitFactorSummary.format(95 / 18.30, 10, 4.0, Optional.empty()));
	}

	@Test
	void beatingThePreviousBestIsANewPersonalBest() {
		assertEquals("Hit Factor: 5.52   (10 rounds, 4.00 s par)\nNew personal best!",
				HitFactorSummary.format(5.52, 10, 4.0, Optional.of(5.19)));
	}

	@Test
	void slowerRunShowsPercentageOfPreviousBest() {
		assertEquals("Hit Factor: 5.19   (10 rounds, 4.00 s par)\n94% of personal best (5.52)",
				HitFactorSummary.format(5.19, 10, 4.0, Optional.of(5.52)));
	}

	@Test
	void withoutBestTrackingOnlyTheHitFactorIsShown() {
		assertEquals("Hit Factor: 5.19   (10 rounds, 4.00 s par)", HitFactorSummary.format(5.19, 10, 4.0));
	}
}
