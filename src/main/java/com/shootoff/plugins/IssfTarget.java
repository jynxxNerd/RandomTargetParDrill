package com.shootoff.plugins;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The drill's ISSF target, with only its inner rings: 10 rings is the full target, fewer rings drop
 * the outer ones. Every ring is {@link #RING_WIDTH} wide and scores as it does on the full target, so
 * the target's radius is the ring count times the ring width.
 */
final class IssfTarget {
	static final int FULL_RINGS = 10;
	static final int RING_WIDTH = 20;
	// The full target's perceived size; smaller targets are perceived in proportion
	private static final int FULL_PERCEIVED_SIZE = 500;
	private static final int DEFAULT_DISTANCE = 3500;

	private IssfTarget() {}

	/**
	 * @return the <tt>.target</tt> file's contents for the innermost <tt>rings</tt> rings
	 */
	static String xml(int rings) {
		final int radius = rings * RING_WIDTH;
		final int perceivedSize = FULL_PERCEIVED_SIZE * rings / FULL_RINGS;
		final StringBuilder xml = new StringBuilder();

		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xml.append(String.format(Locale.ROOT,
				"<target defaultPerceivedHeight=\"%d\" defaultPerceivedWidth=\"%d\" defaultDistance=\"%d\">\n",
				perceivedSize, perceivedSize, DEFAULT_DISTANCE));

		// Each ring is a colored disc with a black disc 1 px smaller on top, leaving its outline; the
		// outer ring's outline is white, the others' gray
		for (int ring = rings; ring >= 1; ring--) {
			final int points = FULL_RINGS + 1 - ring;
			final int ringRadius = ring * RING_WIDTH;
			appendEllipse(xml, radius, ringRadius, ring == rings ? "white" : "gray", points, "1");
			appendEllipse(xml, radius, ringRadius - 1, "black", points, "1");
		}

		// A faint red center over the 10 ring
		appendEllipse(xml, radius, RING_WIDTH - 1, "red", FULL_RINGS, ".1");
		xml.append("</target>\n");

		return xml.toString();
	}

	/**
	 * Writes the target for <tt>rings</tt> rings into <tt>directory</tt>, replacing any earlier copy.
	 *
	 * @return the target file
	 */
	static Path write(Path directory, int rings) throws IOException {
		final Path file = directory.resolve(String.format(Locale.ROOT, "ISSF-%drings.target", rings));
		Files.writeString(file, xml(rings), StandardCharsets.UTF_8);
		return file;
	}

	private static void appendEllipse(StringBuilder xml, int center, int radius, String fill, int points,
			String opacity) {
		xml.append(String.format(Locale.ROOT,
				"\t<ellipse centerX=\"%f\" centerY=\"%f\" radiusX=\"%f\" radiusY=\"%f\" fill=\"%s\">\n",
				(double) center, (double) center, (double) radius, (double) radius, fill));
		xml.append(String.format(Locale.ROOT, "\t\t<tag name=\"points\" value=\"%d\" />\n", points));
		xml.append(String.format(Locale.ROOT, "\t\t<tag name=\"opacity\" value=\"%s\" />\n", opacity));
		xml.append("\t</ellipse>\n");
	}
}
