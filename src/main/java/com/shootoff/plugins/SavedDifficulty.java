package com.shootoff.plugins;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The difficulty of the drill's last run, kept in a properties file so the next run starts on it.
 * Anything missing or unreadable means {@link Difficulty#EASY}.
 */
final class SavedDifficulty {
	private static final Logger logger = LoggerFactory.getLogger(SavedDifficulty.class);

	private static final String KEY = "difficulty";

	private SavedDifficulty() {}

	static Difficulty load(Path file) {
		if (!Files.exists(file)) return Difficulty.EASY;

		final Properties settings = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			settings.load(reader);
		} catch (final IOException e) {
			logger.error("Could not read the saved difficulty in {}", file, e);
			return Difficulty.EASY;
		}

		final String value = settings.getProperty(KEY, Difficulty.EASY.name());
		try {
			return Difficulty.valueOf(value);
		} catch (final IllegalArgumentException e) {
			logger.warn("Unknown saved difficulty {} in {}; starting on easy", value, file);
			return Difficulty.EASY;
		}
	}

	static void save(Path file, Difficulty difficulty) {
		final Properties settings = new Properties();
		settings.setProperty(KEY, difficulty.name());

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			settings.store(writer, "RandomTargetParDrill settings");
		} catch (final IOException e) {
			logger.error("Could not save the difficulty to {}", file, e);
		}
	}
}
