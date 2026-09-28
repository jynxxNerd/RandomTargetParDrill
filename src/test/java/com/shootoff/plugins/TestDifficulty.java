package com.shootoff.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestDifficulty {
	@Test
	void easyIsTheFullTargetAndHarderLevelsKeepFewerRings() {
		assertEquals(10, Difficulty.EASY.rings());
		assertEquals(5, Difficulty.MEDIUM.rings());
		assertEquals(3, Difficulty.HARD.rings());
	}

	@Test
	void nextStepsThroughTheLevelsAndWrapsAround() {
		assertEquals(Difficulty.MEDIUM, Difficulty.EASY.next());
		assertEquals(Difficulty.HARD, Difficulty.MEDIUM.next());
		assertEquals(Difficulty.EASY, Difficulty.HARD.next());
	}

	@Test
	void labelIsTheCapitalizedName() {
		assertEquals("Easy", Difficulty.EASY.label());
		assertEquals("Medium", Difficulty.MEDIUM.label());
		assertEquals("Hard", Difficulty.HARD.label());
	}
}
