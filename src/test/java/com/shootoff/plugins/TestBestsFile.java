package com.shootoff.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestBestsFile {
	private static final String KEY = PersonalBests.settingsKey(10, 4.0);

	@TempDir Path temp;
	private Path data;
	private Path home;

	@BeforeEach
	void setUp() throws IOException {
		data = Files.createDirectories(temp.resolve("data"));
		home = Files.createDirectories(temp.resolve("home"));
	}

	@Test
	void copiesTheV1BestsOnTheFirstRun() throws IOException {
		Files.writeString(home.resolve(RandomTargetParDrill.BESTS_FILE), KEY + "=6.588104617997978\n");

		final Path bests = BestsFile.locate(data, home);

		assertEquals(data.resolve(RandomTargetParDrill.BESTS_FILE), bests);
		assertEquals(Optional.of(6.588104617997978), new PersonalBests(bests).best(KEY));
	}

	@Test
	void leavesTheV1FileForTheV1Drill() throws IOException {
		final Path legacy = home.resolve(RandomTargetParDrill.BESTS_FILE);
		Files.writeString(legacy, KEY + "=6.5\n");

		new PersonalBests(BestsFile.locate(data, home)).recordIfBest(KEY, 7.0);

		assertEquals(KEY + "=6.5\n", Files.readString(legacy));
	}

	@Test
	void neverReplacesBestsAlreadyInTheDataDirectory() throws IOException {
		Files.writeString(home.resolve(RandomTargetParDrill.BESTS_FILE), KEY + "=6.5\n");
		Files.writeString(data.resolve(RandomTargetParDrill.BESTS_FILE), KEY + "=7.0\n");

		BestsFile.locate(data, home);

		assertEquals(KEY + "=7.0\n", Files.readString(data.resolve(RandomTargetParDrill.BESTS_FILE)));
	}

	@Test
	void withoutV1BestsTheDataDirectoryStartsEmpty() throws IOException {
		final Path bests = BestsFile.locate(data, home);

		assertFalse(Files.exists(bests));
		assertEquals(Optional.empty(), new PersonalBests(bests).best(KEY));
	}
}
