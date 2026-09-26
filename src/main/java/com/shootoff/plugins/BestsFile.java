package com.shootoff.plugins;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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
	 * Copies a file; a seam so a test can simulate a copy that fails partway through.
	 */
	interface Copier {
		void copy(Path source, Path target) throws IOException;
	}

	/**
	 * @return the bests file in <tt>dataDirectory</tt>, first copying the v1 drill's file from
	 *         <tt>shootoffHome</tt> if there is one and the copy doesn't exist yet. Neither file is ever
	 *         overwritten, moved or deleted.
	 */
	static Path locate(Path dataDirectory, Path shootoffHome) {
		return locate(dataDirectory, shootoffHome, Files::copy);
	}

	/**
	 * As {@link #locate(Path, Path)}, but with the copy step ({@code copier}) injectable for tests. The
	 * copy lands in a temporary file in {@code dataDirectory} first, then a move (same directory, so a
	 * single rename, not a copy-and-delete) puts it at the final path without ever replacing an existing
	 * file there: nothing is left at the final path unless it's complete. A failed copy, or a move that
	 * loses a race to a concurrent run, deletes the temporary file and changes nothing else.
	 *
	 * <p>
	 * {@link java.nio.file.StandardCopyOption#ATOMIC_MOVE ATOMIC_MOVE} is deliberately not used here: on
	 * this JDK/platform it silently replaces an existing target instead of failing, which is exactly the
	 * clobber this method must not do. The plain move below still resolves to a single, atomic rename
	 * (never a copy-and-delete) because {@code tmp} and {@code bests} are always in the same directory,
	 * and it fails with {@link FileAlreadyExistsException} if {@code bests} already exists.
	 */
	static Path locate(Path dataDirectory, Path shootoffHome, Copier copier) {
		final Path bests = dataDirectory.resolve(RandomTargetParDrill.BESTS_FILE);
		final Path legacy = shootoffHome.resolve(RandomTargetParDrill.BESTS_FILE);

		if (Files.exists(bests) || !Files.isRegularFile(legacy)) return bests;

		// A fresh, not-yet-existing name: the copy creates it, so a plain (non-replacing) copy can't
		// fail just because we picked the name
		final Path tmp = dataDirectory.resolve(RandomTargetParDrill.BESTS_FILE + '.' + UUID.randomUUID() + ".tmp");
		boolean moved = false;

		try {
			copier.copy(legacy, tmp);

			try {
				Files.move(tmp, bests);
				moved = true;
				logger.info("Copied the personal bests in {} to {}", legacy, bests);
			} catch (final FileAlreadyExistsException e) {
				// Another run's copy finished first; keep its file
			}
		} catch (final IOException e) {
			logger.error("Could not copy the personal bests in {} to {}", legacy, bests, e);
		} finally {
			if (!moved) deleteQuietly(tmp);
		}

		return bests;
	}

	private static void deleteQuietly(Path tmp) {
		try {
			Files.deleteIfExists(tmp);
		} catch (final IOException e) {
			logger.error("Could not delete the temporary file {}", tmp, e);
		}
	}
}
