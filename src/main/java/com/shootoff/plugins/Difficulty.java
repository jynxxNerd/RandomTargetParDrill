package com.shootoff.plugins;

import java.util.Locale;

/**
 * How much of the ISSF target the drill shows. Harder levels keep only its inner rings, so the target
 * is smaller and a hit in the same place scores the same at every level.
 */
public enum Difficulty {
	EASY(10), MEDIUM(5), HARD(3);

	private final int rings;

	Difficulty(int rings) {
		this.rings = rings;
	}

	/**
	 * @return how many of the ISSF target's rings, counted from the center, this level keeps
	 */
	public int rings() {
		return rings;
	}

	/**
	 * @return the level after this one, wrapping around to the first
	 */
	public Difficulty next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public String label() {
		return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
	}
}
