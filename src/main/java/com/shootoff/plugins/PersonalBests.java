package com.shootoff.plugins;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Best hit factor per drill settings, stored in a properties file. Hit factors
 * are only comparable between runs with the same settings, so each settings
 * combination keeps its own best.
 */
public final class PersonalBests {
	private final Path file;

	public PersonalBests(Path file) {
		this.file = file;
	}

	public static String settingsKey(int rounds, double parTime) {
		return String.format(Locale.ROOT, "%drounds-%.2fspar", rounds, parTime);
	}

	public Optional<Double> best(String settingsKey) throws IOException {
		final String value = load().getProperty(settingsKey);

		if (value == null) return Optional.empty();

		try {
			return Optional.of(Double.parseDouble(value));
		} catch (final NumberFormatException e) {
			return Optional.empty();
		}
	}

	/**
	 * @return true if hitFactor is a new best for these settings (including
	 *         the first run) and was saved
	 */
	public boolean recordIfBest(String settingsKey, double hitFactor) throws IOException {
		final Optional<Double> best = best(settingsKey);

		if (best.isPresent() && best.get() >= hitFactor) return false;

		final Properties bests = load();
		bests.setProperty(settingsKey, Double.toString(hitFactor));

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			bests.store(writer, "RandomTargetParDrill personal best hit factors");
		}

		return true;
	}

	private Properties load() throws IOException {
		final Properties bests = new Properties();

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				bests.load(reader);
			}
		}

		return bests;
	}
}
