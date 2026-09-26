package com.shootoff.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where the drill keeps its personal bests: the exercise's data directory. The v1 drill kept them in
 * the ShootOFF folder. The first v2 run copies that file and leaves it there for the v1 drill.
 */
final class BestsFile {
	private static final Logger logger = LoggerFactory.getLogger(BestsFile.class);

	private BestsFile() {}

	/**
	 * @return the bests file in <tt>dataDirectory</tt>, first copying the v1 drill's file from
	 *         <tt>shootoffHome</tt> if there is one and the copy doesn't exist yet. Neither file is ever
	 *         overwritten, moved or deleted.
	 */
	static Path locate(Path dataDirectory, Path shootoffHome) {
		final Path bests = dataDirectory.resolve(RandomTargetParDrill.BESTS_FILE);
		final Path legacy = shootoffHome.resolve(RandomTargetParDrill.BESTS_FILE);

		if (Files.exists(bests) || !Files.isRegularFile(legacy)) return bests;

		try {
			Files.copy(legacy, bests);
			logger.info("Copied the personal bests in {} to {}", legacy, bests);
		} catch (final IOException e) {
			logger.error("Could not copy the personal bests in {} to {}", legacy, bests, e);
		}

		return bests;
	}
}
