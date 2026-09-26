package com.shootoff.plugins;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shootoff.camera.Shot;
import com.shootoff.camera.shot.ShotColor;
import com.shootoff.exercise.ButtonHandle;
import com.shootoff.exercise.Cancellable;
import com.shootoff.exercise.DelayRange;
import com.shootoff.exercise.Exercise;
import com.shootoff.exercise.ExerciseHost;
import com.shootoff.exercise.RowStyle;
import com.shootoff.exercise.ShotMarkerHandle;
import com.shootoff.exercise.ShotStyle;
import com.shootoff.exercise.TargetHandle;
import com.shootoff.exercise.TextHandle;
import com.shootoff.exercise.TextStyle;
import com.shootoff.geom.Point;
import com.shootoff.geom.Size;
import com.shootoff.targets.model.Hit;

/**
 * Shows the ISSF target at a random place on the projector arena after a random delay: shoot it
 * before the par time runs out. After the last round it shows the hit factor against the personal
 * best for the same settings, and replays every shot on the target.
 */
public class RandomTargetParDrill implements Exercise {
	private static final Logger logger = LoggerFactory.getLogger(RandomTargetParDrill.class);

	static final String BESTS_FILE = "RandomTargetParDrill-bests.properties";
	static final String TARGET_FILE = "@targets/ISSF.target";
	static final String BACKGROUND = "/backgrounds/blackBG.png";
	static final String BUZZER_WAV = "/sounds/buzzer.wav";
	static final String MAKE_READY_WAV = "sounds/voice/shootoff-makeready.wav";
	static final String BEEP_WAV = "sounds/beep.wav";
	static final String PAUSE = "Pause";
	static final String RESUME = "Resume";
	static final String CLEAR_SHOTS = "Clear Shots";
	static final String ROUNDS_SETTING = "Shots per round";
	static final String LENGTH_COL_NAME = "Length";
	static final String POINTS_COL_NAME = "Score";

	static final double DEFAULT_PAR_TIME = 4.0;
	static final DelayRange DEFAULT_DELAY = new DelayRange(5, 8);
	static final int DEFAULT_MAX_ROUNDS = 10;

	private static final Duration START_DELAY = Duration.ofSeconds(10);
	private static final Duration RESUME_DELAY = Duration.ofSeconds(5);
	private static final Duration SHOOT_TO_RESET_DELAY = Duration.ofSeconds(4);
	private static final Duration RESULTS_DELAY = Duration.ofSeconds(1);
	private static final Duration FINAL_HIDE_DELAY = Duration.ofMillis(500);
	// Targets stay this far from the arena's right and bottom edges; the summary target sits this far
	// up and left of the middle
	private static final int MARGIN = 50;

	private static final TextStyle LABEL_STYLE = new TextStyle(40, "white", "transparent");
	private static final TextStyle TIME_STYLE = new TextStyle(60, "white", "transparent");
	private static final RowStyle PAR_MISS_ROW = new RowStyle("coral");

	private final Random random;
	private ExerciseHost host;
	private PersonalBests personalBests;
	private TargetHandle target;
	private ButtonHandle pauseResumeButton;
	private TextHandle scoreText;
	private TextHandle roundText;
	private TextHandle timeText;

	// Every scheduled task, all cancelled by a reset; the pending round step, replaced by a resume
	private final List<Cancellable> pending = new ArrayList<>();
	private Optional<Cancellable> nextStep = Optional.empty();
	private final List<ShotMarkerHandle> roundMarkers = new ArrayList<>();
	private final List<ShotMarkerHandle> replayMarkers = new ArrayList<>();
	private final List<TrackedShot> trackedShots = new ArrayList<>();

	private double parTime = DEFAULT_PAR_TIME;
	private int delayMin = DEFAULT_DELAY.minSeconds();
	private int delayMax = DEFAULT_DELAY.maxSeconds();
	private int roundLimit = DEFAULT_MAX_ROUNDS;
	private boolean paused = false;
	private boolean repeatExercise = true;
	private boolean countScore = false;
	private boolean shootToReset = false;
	private boolean hadShot = false;
	// Paused during the current round's par time: its end leaves the next round to the resume
	private boolean pausedDuringRound = false;
	private boolean isDrillComplete = false;
	private long beepTime = 0;
	private long roundStartTime = 0;
	private float shotTime;
	private int score = 0;
	private int round = 0;

	/**
	 * A shot or a par miss, with where the target was at the time
	 */
	private record TrackedShot(Optional<Point> position, Point targetPosition, ShotColor color, boolean isHit,
			int points, float shotTime, boolean missedPar) {}

	public RandomTargetParDrill() {
		this(new Random());
	}

	RandomTargetParDrill(Random random) {
		this.random = random;
	}

	@Override
	public ExerciseMetadata metadata() {
		return new ExerciseMetadata("Random Target PAR Drill with Score", "2.0", "Benjamin Fears",
				"Shoot a randomly placed target as fast as you can.", true);
	}

	@Override
	public void start(ExerciseHost host) {
		this.host = host;
		personalBests = new PersonalBests(host.dataDirectory().resolve(BESTS_FILE));

		target = host.addTarget(TARGET_FILE, 0, 0)
				.orElseThrow(() -> new IllegalStateException("Can't load " + TARGET_FILE));
		target.setVisible(false);

		initUI();
		initService();
	}

	@Override
	public void onShot(Shot shot, Optional<Hit> hit) {
		if (hit.isPresent() && shot.getColor() == ShotColor.GREEN) return;

		if (repeatExercise) {
			hadShot = true;
			setLength();
		}

		if (shootToReset) {
			shootToReset = false;
			host.clearShots();
			onReset();
			return;
		}

		roundMarkers.add(host.showShotMarker(shot.getX(), shot.getY(), new ShotStyle(shot.getColor())));
		recordShot(shot, hit);

		if (hit.isEmpty() || !countScore) {
			if (!countScore) {
				logger.debug("count score is false!");
			} else {
				logger.debug("hit is not present!");
				setLastTime("Missed!");
			}
			return;
		}

		String roundScore = "";
		final Optional<String> points = hit.get().region().tag("points");
		if (points.isPresent()) {
			setPoints(shot.getColor(), points.get());
			roundScore += String.format("%d points   -  ", Integer.parseInt(points.get()));
		}

		roundScore += String.format("%.3f seconds", shotTime);
		setLastTime(roundScore);
	}

	@Override
	public void onReset() {
		host.pauseShotDetection(true);
		cancelPending();
		paused = false;
		pauseResumeButton.setLabel(PAUSE);

		hideTargetAndShots();
		removeMarkers(replayMarkers);

		resetValues();
		scheduleNextStep(this::setupWait, RESUME_DELAY);
	}

	@Override
	public void stop() {
		// The host cancels the tasks and removes what the drill added
		repeatExercise = false;
	}

	private void initUI() {
		host.setBackground(BACKGROUND);
		pauseResumeButton = host.addButton(PAUSE, this::pauseOrResume);
		host.addButton(CLEAR_SHOTS, host::clearShots);
		host.addColumn(LENGTH_COL_NAME);
		host.addColumn(POINTS_COL_NAME);

		final Size surface = host.surfaceSize();
		scoreText = host.showText("Score: 0", 10, 10, LABEL_STYLE);
		roundText = host.showText(roundLabel(), surface.getWidth() / 2, 10, LABEL_STYLE);
		timeText = host.showText("", 10, surface.getHeight() - 80, TIME_STYLE);

		host.addNumberSetting(ROUNDS_SETTING, DEFAULT_MAX_ROUNDS, 1, 100, 1, value -> roundLimit = (int) value);
		host.setParTime(DEFAULT_PAR_TIME);
		host.setDelayedStart(DEFAULT_DELAY);
		host.onParTimeChanged(value -> parTime = value);
		host.onDelayedStartChanged(range -> {
			delayMin = range.minSeconds();
			delayMax = range.maxSeconds();
		});
	}

	private void initService() {
		host.pauseShotDetection(true);
		resetValues();
		scheduleNextStep(this::setupWait, START_DELAY);
	}

	private void schedule(Runnable task, Duration delay) {
		// Tasks run on the exercise thread, so this one can't run before it is in the list
		final Cancellable[] handle = new Cancellable[1];
		handle[0] = host.schedule(() -> {
			pending.remove(handle[0]);
			task.run();
		}, delay);
		pending.add(handle[0]);
	}

	// The drill's next step (make ready, or a round): at most one is pending
	private void scheduleNextStep(Runnable step, Duration delay) {
		nextStep.ifPresent(Cancellable::cancel);
		nextStep = Optional.of(host.schedule(step, delay));
	}

	private void cancelPending() {
		pending.forEach(Cancellable::cancel);
		pending.clear();
		nextStep.ifPresent(Cancellable::cancel);
		nextStep = Optional.empty();
	}

	private void pauseOrResume() {
		if (round >= roundLimit) {
			soundBuzzer();
			return;
		}

		if (!paused) {
			paused = true;
			pauseResumeButton.setLabel(RESUME);
			repeatExercise = false;
			if (countScore) pausedDuringRound = true;
			host.pauseShotDetection(true);
			nextStep.ifPresent(Cancellable::cancel);
			nextStep = Optional.empty();
		} else {
			paused = false;
			pauseResumeButton.setLabel(PAUSE);
			repeatExercise = true;
			scheduleNextStep(this::setupWait, RESUME_DELAY);
		}
	}

	private void setupWait() {
		if (!repeatExercise) return;

		host.pauseShotDetection(true);
		host.playSound(MAKE_READY_WAV);
		scheduleNextStep(this::startRound, Duration.ofSeconds(randomDelay()));
	}

	private void startRound() {
		if (!repeatExercise) return;

		countScore = true;
		pausedDuringRound = false;
		round++;
		host.playSound(BEEP_WAV);

		randomizeTarget();
		target.setVisible(true);

		roundText.setText(roundLabel());
		hideLastTime();

		host.pauseShotDetection(false);
		startRoundTimer();
		schedule(this::endRound, Duration.ofMillis(Math.round(parTime * 1000)));
	}

	private void endRound() {
		if (!hadShot) {
			logger.info("Round ended without a shot");
			parMissed();
		}

		soundBuzzer();

		host.pauseShotDetection(true);
		countScore = false;
		checkDrillComplete();

		final int nextDelay = setupRound();
		// After a pause in this round, the resume starts the next one (5 s, then "make ready")
		if (!pausedDuringRound) scheduleNextStep(this::startRound, Duration.ofSeconds(nextDelay));

		if (isDrillComplete) schedule(this::displayResults, RESULTS_DELAY);
	}

	// Hides the target and shots before the next round; returns the delay before it, in seconds
	private int setupRound() {
		hadShot = false;

		final int randomDelay = randomDelay();
		final int randomDelay2 = random.nextInt((Integer.max(delayMax / 2, delayMin) - delayMin) + 1) + delayMin;

		if (isDrillComplete) {
			schedule(this::hideTargetAndShots, FINAL_HIDE_DELAY);
			return 0;
		}

		schedule(this::hideTargetAndShots, Duration.ofSeconds(Integer.min(randomDelay, randomDelay2)));
		return randomDelay;
	}

	private int randomDelay() {
		return random.nextInt((delayMax - delayMin) + 1) + delayMin;
	}

	private void soundBuzzer() {
		host.playSound(BUZZER_WAV);
	}

	private void setLength() {
		final float drawShotLength = (float) (host.currentTimeMillis() - beepTime) / 1000f; // s
		host.setColumnValue(LENGTH_COL_NAME, String.format("%.2f", drawShotLength));
		shotTime = drawShotLength;
	}

	private void recordShot(Shot shot, Optional<Hit> hit) {
		if (shot.getColor() == ShotColor.GREEN) {
			logger.info("Ignored GREEN shot!!!");
			return;
		}

		trackedShots.add(new TrackedShot(Optional.of(new Point(shot.getX(), shot.getY())), target.position(),
				shot.getColor(), hit.isPresent(), points(hit), shotTime, false));
	}

	private static int points(Optional<Hit> hit) {
		return hit.flatMap(h -> h.region().tag("points")).map(Integer::parseInt).orElse(0);
	}

	// v1 added a fake red shot for this row; it is the same row, without the shot
	private void parMissed() {
		host.addTimerRow(host.currentTimeMillis() - roundStartTime, PAR_MISS_ROW);
		setLength();
		trackedShots.add(new TrackedShot(Optional.empty(), target.position(), ShotColor.RED, false, 0, shotTime, true));
		setPoints(ShotColor.RED, "0");
		setLastTime("Par missed!");
	}

	private void checkDrillComplete() {
		if (round >= roundLimit) {
			isDrillComplete = true;
			repeatExercise = false;

			schedule(() -> {
				// Wait 4 seconds to reactivate shot detection: the next shot restarts the drill
				host.pauseShotDetection(false);
				shootToReset = true;
			}, SHOOT_TO_RESET_DELAY);
		}
	}

	private void displayResults() {
		final Size surface = host.surfaceSize();
		final Size size = target.size();
		final double targetX = (surface.getWidth() / 2) - (size.getWidth() / 2) - MARGIN;
		final double targetY = (surface.getHeight() / 2) - (size.getHeight() / 2) - MARGIN;
		target.move(targetX, targetY);
		target.setVisible(true);

		int numShots = 0;
		float timeTotal = 0;
		int numMisses = 0;
		int numParMisses = 0;
		int pointsTotal = 0;
		float maxTime = 0;
		float minTime = 1000;
		float maxScore = 0;
		float minScore = 1000;

		for (final TrackedShot tracked : trackedShots) {
			logger.info(String.format("Shot %d: %.2f - par time = %.2f", numShots, tracked.shotTime(), parTime));
			numShots++;
			if (!tracked.isHit() && !tracked.missedPar()) numMisses++;
			if (tracked.missedPar()) numParMisses++;

			timeTotal += tracked.shotTime();
			minTime = Math.min(tracked.shotTime(), minTime);
			maxTime = Math.max(tracked.shotTime(), maxTime);

			pointsTotal += tracked.points();
			minScore = Math.min(tracked.points(), minScore);
			maxScore = Math.max(tracked.points(), maxScore);

			// Replay the shot where it hit the target, now that the target has moved
			tracked.position().ifPresent(position -> replayMarkers.add(host.showShotMarker(
					targetX - tracked.targetPosition().getX() + position.getX(),
					targetY - tracked.targetPosition().getY() + position.getY(), new ShotStyle(tracked.color()))));
		}

		final float avgTime = timeTotal / numShots;
		final float avgPoints = (float) pointsTotal / numShots;
		final double hitFactor = HitFactor.compute(pointsTotal, numMisses, numParMisses, timeTotal);

		logger.info(String.format(
				"Total Points: %d, Total Time: %.2f; Average Points: %.3f; Average Time: %.3f; Missed Shots: %d; Missed Par: %d; Hit Factor: %.2f",
				pointsTotal, timeTotal, avgPoints, avgTime, numMisses, numParMisses, hitFactor));

		final String message = hitFactorSummary(hitFactor) + "\n\n" + String.format(
				"Total Shots: %d\nTotal Points: %d\nTotal Time: %.2f\nAverage Points: %.3f\nAverage Time: %.3f\nPoints min/max: %.2f/%.2f\nTimes min/max: %.3f/%.3f\nMissed Shots: %d\nMissed Par: %d",
				numShots, pointsTotal, timeTotal, avgPoints, avgTime, minScore, maxScore, minTime, maxTime, numMisses,
				numParMisses);
		showOnFeeds(message);

		schedule(this::hideLastTime, RESULTS_DELAY);
	}

	private String hitFactorSummary(double hitFactor) {
		final String settingsKey = PersonalBests.settingsKey(roundLimit, parTime);

		try {
			final Optional<Double> previousBest = personalBests.best(settingsKey);
			personalBests.recordIfBest(settingsKey, hitFactor);
			return HitFactorSummary.format(hitFactor, roundLimit, parTime, previousBest);
		} catch (final IOException e) {
			logger.error("Could not read or save personal best hit factors", e);
			return HitFactorSummary.format(hitFactor, roundLimit, parTime);
		}
	}

	private void randomizeTarget() {
		final Size surface = host.surfaceSize();
		final Size size = target.size();
		logger.info(String.format("Target dimensions: w: %.1f, h: %.1f", size.getWidth(), size.getHeight()));

		final int maxX = (int) (surface.getWidth() - size.getWidth() - MARGIN);
		final int x = random.nextInt(maxX);
		final int maxY = (int) (surface.getHeight() - size.getHeight() - MARGIN);
		final int y = random.nextInt(maxY);

		logger.info(String.format("Placing target at x: %d, y: %d", x, y));
		target.move(x, y);
	}

	private void hideTargetAndShots() {
		target.setVisible(false);
		removeMarkers(roundMarkers);
	}

	private static void removeMarkers(List<ShotMarkerHandle> markers) {
		markers.forEach(ShotMarkerHandle::remove);
		markers.clear();
	}

	private String roundLabel() {
		return String.format("Round: %d/%d", round, roundLimit);
	}

	private void hideLastTime() {
		timeText.setText("");
	}

	private void setLastTime(String time) {
		timeText.setText(time);
	}

	// v1's showTextOnFeed on the projector: the arena's score text and every camera feed
	private void showOnFeeds(String message) {
		scoreText.setText(message);
		host.showMessage(message);
	}

	private void startRoundTimer() {
		beepTime = host.currentTimeMillis();
		if (roundStartTime == 0) roundStartTime = beepTime;
	}

	private void resetValues() {
		shootToReset = false;
		isDrillComplete = false;
		// A reset during a round (after a shot, or a pause) mustn't carry over into the next one
		hadShot = false;
		countScore = false;
		pausedDuringRound = false;
		repeatExercise = true;
		roundStartTime = 0;
		score = 0;
		round = 0;
		trackedShots.clear();

		showOnFeeds("Score: 0");
		roundText.setText(roundLabel());
		hideLastTime();
	}

	private void setPoints(ShotColor shotColor, String points) {
		host.setColumnValue(POINTS_COL_NAME, points);

		if (shotColor == ShotColor.RED || shotColor == ShotColor.INFRARED) {
			score += Integer.parseInt(points);
		}

		showOnFeeds(String.format("Score: %d", score));
	}
}
