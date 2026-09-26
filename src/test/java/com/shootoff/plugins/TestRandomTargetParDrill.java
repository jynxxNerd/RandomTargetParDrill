package com.shootoff.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.shootoff.camera.shot.ShotColor;
import com.shootoff.exercise.DelayRange;
import com.shootoff.exercise.FakeExerciseHost;
import com.shootoff.exercise.RowStyle;
import com.shootoff.exercise.ShotStyle;
import com.shootoff.exercise.TargetHandle;
import com.shootoff.geom.Point;

class TestRandomTargetParDrill {
	@TempDir Path temp;
	private Locale previousLocale;
	private FakeExerciseHost host;

	@BeforeEach
	void setUp() {
		previousLocale = Locale.getDefault();
		// The drill formats times in the default locale, as it always has
		Locale.setDefault(Locale.US);
		host = new FakeExerciseHost(FakeExerciseHost.DEFAULT_SURFACE, true, temp.resolve("data"));
	}

	@AfterEach
	void tearDown() {
		Locale.setDefault(previousLocale);
	}

	// Starts a drill of the given rounds with a 1 s start delay and a 2 s par time, and runs it to its
	// first round: the target shows 11 s after the start (the 10 s start delay, then 1 s)
	private void startDrill(int rounds) {
		host.start(new RandomTargetParDrill(new Random(42)));
		host.changeDelayedStart(new DelayRange(1, 1));
		host.changeParTime(2.0);
		host.changeSetting(RandomTargetParDrill.ROUNDS_SETTING, rounds);
		host.advance(Duration.ofSeconds(11));
	}

	private TargetHandle target() {
		return host.targets().get(0);
	}

	// The ISSF target is 400 x 400; its 10-point center is 200, 200 into it
	private void shootCenter() {
		final Point position = target().position();
		assertTrue(host.shoot(ShotColor.RED, position.getX() + 200, position.getY() + 200));
	}

	private String scoreText() {
		return host.textAt(10, 10).orElseThrow();
	}

	private String timeText() {
		return host.textAt(10, 640).orElseThrow();
	}

	private Optional<String> roundText() {
		return host.textAt(640, 10);
	}

	private static long count(List<String> sounds, String sound) {
		return sounds.stream().filter(sound::equals).count();
	}

	@Test
	void fullDrillScoresTenRoundsAndReplaysTheShotsOnTheSummaryTarget() {
		startDrill(10);

		for (int round = 1; round <= 10; round++) {
			assertTrue(host.isVisible(target()), "round " + round);
			assertEquals(Optional.of("Round: " + round + "/10"), roundText());

			host.advance(Duration.ofMillis(500));
			shootCenter();
			assertEquals("10 points   -  0.500 seconds", timeText());

			// The par time ends 2 s after the beep; the next round starts 1 s later
			host.advance(Duration.ofMillis(2500));
		}

		assertEquals(10, count(host.sounds(), RandomTargetParDrill.BEEP_WAV));
		assertEquals(10, count(host.sounds(), RandomTargetParDrill.BUZZER_WAV));
		assertEquals(10, host.rows().size());
		for (final FakeExerciseHost.Row row : host.rows()) {
			assertEquals("0.50", row.values().get(RandomTargetParDrill.LENGTH_COL_NAME));
			assertEquals("10", row.values().get(RandomTargetParDrill.POINTS_COL_NAME));
		}
		assertTrue(host.messages().contains("Score: 100"));

		final String summary = "Hit Factor: 20.00   (10 rounds, 2.00 s par)\nNew personal best!\n\n"
				+ "Total Shots: 10\nTotal Points: 100\nTotal Time: 5.00\nAverage Points: 10.000\n"
				+ "Average Time: 0.500\nPoints min/max: 10.00/10.00\nTimes min/max: 0.500/0.500\n"
				+ "Missed Shots: 0\nMissed Par: 0";
		assertEquals(summary, scoreText());
		assertEquals(summary, host.messages().get(host.messages().size() - 1));

		// The summary target sits up and left of the middle of the arena, and every shot is replayed
		// where it hit the target: its center
		assertEquals(new Point(390, 110), target().position());
		assertTrue(host.isVisible(target()));
		assertEquals(10, host.shotMarkers().size());
		for (final FakeExerciseHost.ShownMarker marker : host.shotMarkers()) {
			assertEquals(590, marker.x(), 1e-9);
			assertEquals(310, marker.y(), 1e-9);
		}
	}

	@Test
	void parMissIsCountedAndPenalized() {
		startDrill(1);

		host.advance(Duration.ofSeconds(2));
		assertEquals("Par missed!", timeText());

		// The coral row v1 showed, 2 s after the drill's first beep, with no shot behind it
		assertEquals(1, host.rows().size());
		final FakeExerciseHost.Row row = host.rows().get(0);
		assertEquals(Optional.empty(), row.shot());
		assertEquals(2000, row.timeMillis());
		assertEquals(Optional.of(new RowStyle("coral")), row.style());
		assertEquals("2.00", row.values().get(RandomTargetParDrill.LENGTH_COL_NAME));
		assertEquals("0", row.values().get(RandomTargetParDrill.POINTS_COL_NAME));

		host.advance(Duration.ofSeconds(1));
		assertEquals("Hit Factor: 0.00   (1 rounds, 2.00 s par)\nNew personal best!\n\n"
				+ "Total Shots: 1\nTotal Points: 0\nTotal Time: 2.00\nAverage Points: 0.000\n"
				+ "Average Time: 2.000\nPoints min/max: 0.00/0.00\nTimes min/max: 2.000/2.000\n"
				+ "Missed Shots: 0\nMissed Par: 1", scoreText());
		assertEquals(List.of(), host.shotMarkers());
	}

	@Test
	void missIsCountedAndPenalized() {
		startDrill(1);

		host.advance(Duration.ofMillis(500));
		final Point position = target().position();
		// Past the target's bottom-right corner, still on the arena
		assertTrue(host.shoot(ShotColor.RED, position.getX() + 410, position.getY() + 410));
		assertEquals("Missed!", timeText());

		host.advance(Duration.ofMillis(2500));
		assertEquals("Hit Factor: 0.00   (1 rounds, 2.00 s par)\nNew personal best!\n\n"
				+ "Total Shots: 1\nTotal Points: 0\nTotal Time: 0.50\nAverage Points: 0.000\n"
				+ "Average Time: 0.500\nPoints min/max: 0.00/0.00\nTimes min/max: 0.500/0.500\n"
				+ "Missed Shots: 1\nMissed Par: 0", scoreText());
		assertEquals(List.of(new FakeExerciseHost.ShownMarker(800, 520, new ShotStyle(ShotColor.RED))),
				host.shotMarkers());
	}

	@Test
	void pauseAndResumeRestartTheCountdown() {
		host.start(new RandomTargetParDrill(new Random(42)));
		host.changeDelayedStart(new DelayRange(1, 1));
		host.advance(Duration.ofSeconds(10));
		assertEquals(List.of(RandomTargetParDrill.MAKE_READY_WAV), host.sounds());

		host.advance(Duration.ofMillis(500));
		host.click(RandomTargetParDrill.PAUSE);
		assertEquals(List.of(RandomTargetParDrill.RESUME, RandomTargetParDrill.CLEAR_SHOTS), host.buttonLabels());
		assertTrue(host.isShotDetectionPaused());

		// Resuming before the paused round would have started must not start two rounds
		host.advance(Duration.ofMillis(100));
		host.click(RandomTargetParDrill.RESUME);
		assertEquals(List.of(RandomTargetParDrill.PAUSE, RandomTargetParDrill.CLEAR_SHOTS), host.buttonLabels());
		host.advance(Duration.ofMillis(4900));
		assertEquals(List.of(RandomTargetParDrill.MAKE_READY_WAV), host.sounds());
		assertFalse(host.isVisible(target()));

		// 5 s after resuming: "make ready", then the round 1 s later
		host.advance(Duration.ofMillis(100));
		assertEquals(List.of(RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.MAKE_READY_WAV), host.sounds());
		host.advance(Duration.ofSeconds(1));
		assertEquals(List.of(RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.MAKE_READY_WAV,
				RandomTargetParDrill.BEEP_WAV), host.sounds());
		assertTrue(host.isVisible(target()));
		assertFalse(host.isShotDetectionPaused());
		assertEquals(Optional.of("Round: 1/10"), roundText());
	}

	@Test
	void resetAfterAShotStillCountsTheNextRoundsParMiss() {
		startDrill(1);

		host.advance(Duration.ofMillis(500));
		shootCenter();
		host.reset();

		// 5 s after the reset: "make ready", then the round 1 s later, and its par time runs out
		host.advance(Duration.ofSeconds(6));
		assertTrue(host.isVisible(target()));
		host.advance(Duration.ofSeconds(2));
		assertEquals("Par missed!", timeText());

		assertEquals(1, host.rows().size());
		final FakeExerciseHost.Row row = host.rows().get(0);
		assertEquals(Optional.empty(), row.shot());
		assertEquals(2000, row.timeMillis());
		assertEquals(Optional.of(new RowStyle("coral")), row.style());
		assertEquals("0", row.values().get(RandomTargetParDrill.POINTS_COL_NAME));

		host.advance(Duration.ofSeconds(1));
		assertTrue(scoreText().contains("\nTotal Shots: 1\nTotal Points: 0\n"), scoreText());
		assertTrue(scoreText().endsWith("\nMissed Shots: 0\nMissed Par: 1"), scoreText());
	}

	@Test
	void pauseAndResumeDuringTheParTimeRestartTheCountdown() {
		startDrill(2);
		final List<String> firstRound = List.of(RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.BEEP_WAV);
		assertEquals(firstRound, host.sounds());

		host.advance(Duration.ofMillis(500));
		host.click(RandomTargetParDrill.PAUSE);
		host.advance(Duration.ofMillis(100));
		host.click(RandomTargetParDrill.RESUME);

		// The pause ended the round, with no par miss; the next round waits for the resume: 5 s after
		// it, "make ready", then the round after the random delay
		assertFalse(host.isVisible(target()));
		host.advance(Duration.ofMillis(4900));
		assertEquals(firstRound, host.sounds());
		host.advance(Duration.ofMillis(100));
		assertEquals(List.of(RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.BEEP_WAV,
				RandomTargetParDrill.MAKE_READY_WAV), host.sounds());
		host.advance(Duration.ofMillis(999));
		assertEquals(3, host.sounds().size());
		host.advance(Duration.ofMillis(1));
		assertEquals(RandomTargetParDrill.BEEP_WAV, host.sounds().get(3));
		assertEquals(Optional.of("Round: 2/2"), roundText());

		// One round chain: round 2 ends the drill, and nothing else starts
		host.advance(Duration.ofSeconds(30));
		assertEquals(List.of(RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.BEEP_WAV,
				RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.BEEP_WAV, RandomTargetParDrill.BUZZER_WAV),
				host.sounds());
		assertEquals(1, host.rows().size());
		assertTrue(scoreText().endsWith("\nMissed Par: 1"), scoreText());
	}

	@Test
	void pausedRoundEndsAtThePauseEvenWhenItsParTimeOutlastsTheResume() {
		host.start(new RandomTargetParDrill(new Random(42)));
		host.changeDelayedStart(new DelayRange(1, 1));
		host.changeParTime(15.0);
		host.changeSetting(RandomTargetParDrill.ROUNDS_SETTING, 2);
		host.advance(Duration.ofSeconds(11));

		// Round 1 starts at 11 s; pause and resume in its par time, which would run to 26 s
		host.advance(Duration.ofMillis(500));
		host.click(RandomTargetParDrill.PAUSE);
		host.advance(Duration.ofMillis(100));
		host.click(RandomTargetParDrill.RESUME);

		// Round 2 starts 5 s after the resume, then 1 s: at 17.6 s
		host.advance(Duration.ofSeconds(6));
		assertEquals(List.of(RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.BEEP_WAV,
				RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.BEEP_WAV), host.sounds());
		assertEquals(Optional.of("Round: 2/2"), roundText());

		host.advance(Duration.ofMillis(500));
		shootCenter();
		assertEquals("10 points   -  0.500 seconds", timeText());

		// Past round 1's old par end (26 s): round 2 is still the one running
		host.advance(Duration.ofSeconds(9));
		assertEquals(List.of(RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.BEEP_WAV,
				RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.BEEP_WAV), host.sounds());
		assertTrue(host.isVisible(target()));
		assertFalse(host.isShotDetectionPaused());
		assertEquals(Optional.of("Round: 2/2"), roundText());

		// Round 2's own par time ends at 32.6 s, with its shot scored; then the summary, and nothing more
		host.advance(Duration.ofSeconds(30));
		assertEquals(List.of(RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.BEEP_WAV,
				RandomTargetParDrill.MAKE_READY_WAV, RandomTargetParDrill.BEEP_WAV, RandomTargetParDrill.BUZZER_WAV),
				host.sounds());
		assertEquals(1, host.rows().size());
		assertEquals("0.50", host.rows().get(0).values().get(RandomTargetParDrill.LENGTH_COL_NAME));
		assertEquals("10", host.rows().get(0).values().get(RandomTargetParDrill.POINTS_COL_NAME));
		assertEquals("Hit Factor: 20.00   (2 rounds, 15.00 s par)\nNew personal best!\n\n"
				+ "Total Shots: 1\nTotal Points: 10\nTotal Time: 0.50\nAverage Points: 10.000\n"
				+ "Average Time: 0.500\nPoints min/max: 10.00/10.00\nTimes min/max: 0.500/0.500\n"
				+ "Missed Shots: 0\nMissed Par: 0", scoreText());
	}

	@Test
	void summaryComparesTheHitFactorWithThePersonalBest() throws IOException {
		final Path bests = temp.resolve("data").resolve(RandomTargetParDrill.BESTS_FILE);
		Files.writeString(bests, "1rounds-2.00spar=40.0\n");

		startDrill(1);
		host.advance(Duration.ofMillis(500));
		shootCenter();
		host.advance(Duration.ofMillis(2500));

		assertTrue(scoreText().startsWith("Hit Factor: 20.00   (1 rounds, 2.00 s par)\n"
				+ "50% of personal best (40.00)\n\nTotal Shots: 1\nTotal Points: 10\n"), scoreText());
		// A worse run keeps the best
		assertEquals(Optional.of(40.0), new PersonalBests(bests).best("1rounds-2.00spar"));
	}
}
