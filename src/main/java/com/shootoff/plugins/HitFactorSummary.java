package com.shootoff.plugins;

import java.util.Locale;
import java.util.Optional;

/**
 * Text shown at the end of a drill for its hit factor.
 */
public final class HitFactorSummary {
	private HitFactorSummary() {}

	public static String format(double hitFactor, Difficulty difficulty, int rounds, double parTime) {
		return String.format(Locale.ROOT, "Hit Factor: %.2f   (%s, %d rounds, %.2f s par)", hitFactor,
				difficulty.label(), rounds, parTime);
	}

	/**
	 * @param previousBest
	 *            the best hit factor for these settings before this run, if
	 *            any
	 */
	public static String format(double hitFactor, Difficulty difficulty, int rounds, double parTime,
			Optional<Double> previousBest) {
		final String hitFactorLine = format(hitFactor, difficulty, rounds, parTime);

		if (!previousBest.isPresent() || hitFactor > previousBest.get()) {
			return hitFactorLine + "\nNew personal best!";
		}

		// Floor so a run just short of the best never reads as 100%
		final int percentOfBest = (int) Math.floor(100 * hitFactor / previousBest.get());

		return hitFactorLine
				+ String.format(Locale.ROOT, "\n%d%% of personal best (%.2f)", percentOfBest, previousBest.get());
	}
}
