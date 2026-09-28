package com.shootoff.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestPersonalBests {
	@TempDir Path tempDir;

	private static final String KEY = PersonalBests.settingsKey(Difficulty.EASY, 10, 4.0);

	private PersonalBests newBests() {
		return new PersonalBests(tempDir.resolve("bests.properties"));
	}

	@Test
	void settingsKeyNamesRoundsAndPar() {
		assertEquals("10rounds-4.00spar", KEY);
	}

	@Test
	void harderLevelsPrefixTheKeyWithTheirName() {
		assertEquals("medium-10rounds-4.00spar", PersonalBests.settingsKey(Difficulty.MEDIUM, 10, 4.0));
		assertEquals("hard-10rounds-4.00spar", PersonalBests.settingsKey(Difficulty.HARD, 10, 4.0));
	}

	@Test
	void firstRunHasNoBestAndBecomesTheBest() throws IOException {
		final PersonalBests bests = newBests();

		assertEquals(Optional.empty(), bests.best(KEY));
		assertTrue(bests.recordIfBest(KEY, 5.19));
		assertEquals(Optional.of(5.19), bests.best(KEY));
	}

	@Test
	void betterRunReplacesTheBestAndPersists() throws IOException {
		newBests().recordIfBest(KEY, 5.19);

		assertTrue(newBests().recordIfBest(KEY, 5.52));
		assertEquals(Optional.of(5.52), newBests().best(KEY));
	}

	@Test
	void worseRunKeepsTheOldBest() throws IOException {
		newBests().recordIfBest(KEY, 5.52);

		assertFalse(newBests().recordIfBest(KEY, 5.19));
		assertEquals(Optional.of(5.52), newBests().best(KEY));
	}

	@Test
	void differentSettingsKeepSeparateBests() throws IOException {
		final PersonalBests bests = newBests();
		final String otherKey = PersonalBests.settingsKey(Difficulty.EASY, 5, 2.5);

		bests.recordIfBest(KEY, 5.19);

		assertEquals(Optional.empty(), bests.best(otherKey));
		assertTrue(bests.recordIfBest(otherKey, 1.0));
		assertEquals(Optional.of(5.19), bests.best(KEY));
	}

	@Test
	void unreadableValueIsTreatedAsNoBest() throws IOException {
		Files.write(tempDir.resolve("bests.properties"), (KEY + "=not-a-number\n").getBytes(StandardCharsets.UTF_8));
		final PersonalBests bests = newBests();

		assertEquals(Optional.empty(), bests.best(KEY));
		assertTrue(bests.recordIfBest(KEY, 3.0));
		assertEquals(Optional.of(3.0), bests.best(KEY));
	}
}
