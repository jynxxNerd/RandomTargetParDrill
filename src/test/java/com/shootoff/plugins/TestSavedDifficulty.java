package com.shootoff.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestSavedDifficulty {
	@TempDir Path temp;

	private Path file() {
		return temp.resolve("settings.properties");
	}

	@Test
	void noSavedDifficultyIsEasy() {
		assertEquals(Difficulty.EASY, SavedDifficulty.load(file()));
	}

	@Test
	void savedDifficultyIsLoadedBack() {
		SavedDifficulty.save(file(), Difficulty.HARD);

		assertEquals(Difficulty.HARD, SavedDifficulty.load(file()));
	}

	@Test
	void unknownDifficultyIsEasy() throws IOException {
		Files.writeString(file(), "difficulty=IMPOSSIBLE\n");

		assertEquals(Difficulty.EASY, SavedDifficulty.load(file()));
	}
}
