package com.shootoff.plugins;

/**
 * USPSA-style hit factor: points (after miss penalties) per second.
 */
public final class HitFactor {
	public static final int MISS_PENALTY = 10;

	private HitFactor() {}

	/**
	 * @param points
	 *            total points scored
	 * @param misses
	 *            shots that hit nothing
	 * @param parMisses
	 *            rounds with no shot before the par buzzer
	 * @param totalTime
	 *            total time in seconds, including the full par time of par
	 *            misses
	 * @return points minus {@link #MISS_PENALTY} per miss and par miss, floored
	 *         at zero, divided by total time; zero when no time elapsed
	 */
	public static double compute(int points, int misses, int parMisses, double totalTime) {
		if (totalTime <= 0) return 0;

		final int penalizedPoints = Math.max(0, points - MISS_PENALTY * (misses + parMisses));

		return penalizedPoints / totalTime;
	}
}
