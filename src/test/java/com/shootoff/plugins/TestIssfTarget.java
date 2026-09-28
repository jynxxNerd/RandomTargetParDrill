package com.shootoff.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestIssfTarget {
	@TempDir Path temp;

	private static final Pattern RADIUS = Pattern.compile("radiusX=\"([0-9.]+)\"");
	private static final Pattern POINTS = Pattern.compile("name=\"points\" value=\"([0-9]+)\"");

	private static List<String> all(Pattern pattern, String xml) {
		final List<String> values = new ArrayList<>();
		final Matcher matcher = pattern.matcher(xml);
		while (matcher.find()) values.add(matcher.group(1));
		return values;
	}

	private static long count(String regex, String xml) {
		return Pattern.compile(regex).matcher(xml).results().count();
	}

	@Test
	void tenRingsIsTheOriginalIssfTarget() throws IOException {
		try (InputStream original = getClass().getResourceAsStream("/ISSF.target")) {
			assertEquals(new String(original.readAllBytes(), StandardCharsets.UTF_8), IssfTarget.xml(10));
		}
	}

	@Test
	void fiveRingsKeepsTheInnerRingsCenteredInAHalfSizeTarget() {
		final String xml = IssfTarget.xml(5);

		assertEquals(List.of("100.000000", "99.000000", "80.000000", "79.000000", "60.000000", "59.000000",
				"40.000000", "39.000000", "20.000000", "19.000000", "19.000000"), all(RADIUS, xml));
		assertEquals(List.of("6", "6", "7", "7", "8", "8", "9", "9", "10", "10", "10"), all(POINTS, xml));
		assertEquals(11, count("centerX=\"100.000000\" centerY=\"100.000000\"", xml));
		assertEquals(1, count("fill=\"white\"", xml));
		assertEquals(1,
				count("defaultPerceivedHeight=\"250\" defaultPerceivedWidth=\"250\" defaultDistance=\"3500\"", xml));
	}

	@Test
	void writeSavesTheTargetNamedByItsRingCount() throws IOException {
		final Path file = IssfTarget.write(temp, 3);

		assertEquals(temp.resolve("ISSF-3rings.target"), file);
		assertEquals(IssfTarget.xml(3), Files.readString(file));
	}
}
