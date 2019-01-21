package com.shootoff.plugins;

import com.shootoff.camera.Shot;
import com.shootoff.camera.shot.ArenaShot;
import com.shootoff.camera.shot.DisplayShot;
import com.shootoff.camera.shot.ShotColor;
import com.shootoff.gui.DelayedStartListener;
import com.shootoff.gui.ParListener;
import com.shootoff.gui.RoundLimitListener;
import com.shootoff.targets.Hit;
import com.shootoff.targets.Target;
import com.shootoff.targets.TargetRegion;
import com.shootoff.util.NamedThreadFactory;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RandomTargetParDrill extends ProjectorTrainingExerciseBase implements RoundLimitListener, ParListener, TrainingExercise, DelayedStartListener {
	private static final Logger logger = LoggerFactory.getLogger(RandomTargetParDrill.class);

	private static final String TARGET_FILE = "@targets/ISSF.target";
	private static final String PAUSE = "Pause";
	private static final String RESUME = "Resume";

	private static final String LENGTH_COL_NAME = "Length";
	private static final int LENGTH_COL_WIDTH = 60;
	private static final String POINTS_COL_NAME = "Score";
	private static final int POINTS_COL_WIDTH = 60;

	private static final int MAX_ROUNDS = 10;
	private static final int START_DELAY = 10; // s
	private static final int RESUME_DELAY = 5; // s
	private static final int CORE_POOL_SIZE = 2;

	private Target target;

	private Button pauseResumeButton;
	private final Label roundLabel = new Label();
	private final Label timeLabel = new Label();
	private Font arenaFont = new Font(null, 40);

	private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
			new NamedThreadFactory("RandomScoredTargetWithParLimitedShot"));

	private double parTime = 2.0;
	private int delayMin = 4;
	private int delayMax = 8;
	private int roundLimit = MAX_ROUNDS;
	private boolean repeatExercise = true;
	private boolean countScore = false;
	private boolean shootToReset = false;
	private boolean hadShot = false;
	private long beepTime = 0;
	private long roundStartTime = 0;
	private float shotTime;
	private int score = 0;
	private int round = 0;
	private boolean coloredRows = false;

	private List<TrackedShot> trackedShots = new LinkedList<>();


	public RandomTargetParDrill() {
	}

	public RandomTargetParDrill(List<Target> targets) {
		super(targets);
	}

	@Override
	public ExerciseMetadata getInfo() {
		return new ExerciseMetadata("Random Target PAR Drill with Score", "1.0", "Benjamin Fears",
				"Shoot a randomly placed target as fast as you can.");
	}

	@Override
	public void init() {
		createTarget();
		initUI();
		initService();
	}

	@Override
	public void reset(List<Target> targets) {
		roundStartTime = 0;
		repeatExercise = false;
		pauseShotDetection(true);
		executorService.shutdownNow();
		pauseResumeButton.setText(PAUSE);
		resetValues();
		repeatExercise = true;
		trackedShots.clear();

		executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
				new NamedThreadFactory("RandomTargetParDrill"));
		executorService.schedule(new RandomTargetParDrill.SetupWait(), RESUME_DELAY, TimeUnit.SECONDS);
	}

	@Override
	public void destroy() {
		repeatExercise = false;
		removeRoundLabel();
		removeTimeLabel();
		executorService.shutdownNow();
		super.destroy();
	}

	protected class SetupWait implements Runnable {
		@Override
		public void run() {
			if (!repeatExercise) return;

			pauseShotDetection(true);
			playSound(new File("sounds/voice/shootoff-makeready.wav"));
			final int randomDelay = new Random().nextInt((delayMax - delayMin) + 1) + delayMin;

			if (repeatExercise) {
				executorService.schedule(new RandomTargetParDrill.Round(), randomDelay, TimeUnit.SECONDS);
			}

			return;
		}
	}

	protected class TargetHider implements Runnable {
		@Override
		public void run() {
			hideTarget();
			hideShots();
		}
	}

	protected class Round implements Runnable {
		@Override
		public void run() {
			if (repeatExercise) {
				doRound();
				executorService.schedule(new RandomTargetParDrill.Round(), setupRound(), TimeUnit.SECONDS);
			}

			return;
		}
	}

	protected void initUI() {

		pauseResumeButton = addShootOFFButton(PAUSE, (event) -> {
			if(round >= roundLimit){
				InputStream buzzer = getSoundStream("/sounds/buzzer.wav");
				TrainingExerciseBase.playSound(buzzer);
				return;
			}

			final Button pauseResumeButton = (Button) event.getSource();

			if (PAUSE.equals(pauseResumeButton.getText())) {
				pauseResumeButton.setText(RESUME);
				repeatExercise = false;
				pauseShotDetection(true);
			} else {
				pauseResumeButton.setText(PAUSE);
				repeatExercise = true;
				executorService.schedule(new RandomTargetParDrill.SetupWait(), RESUME_DELAY, TimeUnit.SECONDS);
			}
		});

		addShootOFFButton("Clear Shots", (event) -> super.clearShots());
		addShotTimerColumn(LENGTH_COL_NAME, LENGTH_COL_WIDTH);
		addShotTimerColumn(POINTS_COL_NAME, POINTS_COL_WIDTH);


		showTextOnFeed("Score: 0",10,10, Color.TRANSPARENT, Color.WHITE, this.arenaFont);
		initRoundLabel();
		initTimeLabel();
		addRoundLimitExcersizePane();
	}


	private void setLength() {
		final float drawShotLength = (float) (System.currentTimeMillis() - beepTime) / (float) 1000; // s
		setShotTimerColumnText(LENGTH_COL_NAME, String.format("%.2f", drawShotLength));
		this.shotTime = drawShotLength;
	}

	private void initRoundLabel(){
		String roundText = String.format("Round: %d/%d", 0, roundLimit);
		roundLabel.setText(roundText);
		Platform.runLater(() -> {
			this.getArenaPane().getCanvasManager().getCanvasGroup().getChildren().add(roundLabel);
			roundLabel.setLayoutX(this.getArenaWidth() / 2);
			roundLabel.setLayoutY(10);
			roundLabel.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
			roundLabel.setTextFill(Color.WHITE);
			roundLabel.setFont(this.arenaFont);
		});
	}

	private void initTimeLabel(){
		String roundText = String.format("%d", 0);
		timeLabel.setText(roundText);
		Platform.runLater(() -> {
			this.getArenaPane().getCanvasManager().getCanvasGroup().getChildren().add(timeLabel);
			timeLabel.setLayoutX(10);
			timeLabel.setLayoutY(this.getArenaHeight() - 80);
			timeLabel.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
			timeLabel.setTextFill(Color.WHITE);
			timeLabel.setFont(new Font(null, 60));
			timeLabel.setVisible(false);
		});
	}

	private void initService() {
		pauseShotDetection(true);
		resetValues();

		executorService.schedule(new RandomTargetParDrill.SetupWait(), START_DELAY, TimeUnit.SECONDS);
	}

	private void createTarget(){
		Optional<Target> target = super.addTarget(new File(TARGET_FILE), 0,0);
		if(target.isPresent()) {
			target.get().setVisible(false);
			this.target = target.get();
		}
	}

	private int setupRound() {
		if (hadShot) {
			coloredRows = !coloredRows;
			hadShot = false;
		}
		setShotTimerRowColor(null);

		final int randomDelay = new Random().nextInt((delayMax - delayMin) + 1) + delayMin;
		final int randomDelay2 = new Random().nextInt((Integer.max((delayMax/2), delayMin) - delayMin) + 1) + delayMin;
		executorService.schedule(new RandomTargetParDrill.TargetHider(), Integer.min(randomDelay, randomDelay2), TimeUnit.SECONDS);
		return randomDelay;
	}

	private void doRound() {
		countScore = true;
		round++;
		playSound("sounds/beep.wav");

		randomizeTargets();
		showTarget();

		updateRoundLabel();
		hideLastTime();

		pauseShotDetection(false);
		startRoundTimer();

		try {
			Thread.sleep((long) (parTime * 1000.));
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}

		if(!hadShot){
			logger.info("Round ended without a shot");
			parMissed();
		}

		InputStream buzzer = getSoundStream("/sounds/buzzer.wav");
		TrainingExerciseBase.playSound(buzzer);
		pauseShotDetection(true);
		countScore = false;
		endRound();
	}

	private InputStream getSoundStream(String soundResource){
		return new BufferedInputStream(RandomTargetParDrill.class.getResourceAsStream(soundResource));
	}

	@Override
	public void shotListener(Shot shot, Optional<Hit> hit) {
		if(hit.isPresent() && hit.get().getShot().getColor().equals(ShotColor.GREEN)){
			return;
		}

		if (repeatExercise) {
			hadShot = true;
			setLength();
		}

		if(shootToReset){
			shootToReset = false;
			this.reset();
			return;
		}

		drawShot((ArenaShot)shot);
		recordShot((ArenaShot)shot, hit);

		if (!hit.isPresent() || !countScore) {
			if(!countScore){
				logger.debug("count score is false!");
			} else {
				logger.debug("hit is not present!");
				setLastTime("Missed!");
			}
			return;
		}

		Bounds bounds = ((Node)hit.get().getHitRegion()).getBoundsInParent();
		logger.info(String.format("Region bounds in parent: top: %.2f, left: %.2f; Hit in region: x: %d, y: %d", bounds.getMinY(), bounds.getMinX(), hit.get().getImpactX(), hit.get().getImpactY()));

		String roundScore = "";
		final TargetRegion r = hit.get().getHitRegion();
		if (r.tagExists("points")) {
			String points = r.getTag("points");
			setPoints(shot.getColor(), points);
			roundScore += String.format("%d points   -  ", Integer.parseInt(points));
		}

		roundScore += String.format("%.3f seconds", shotTime);
		setLastTime(roundScore);
	}

	private void recordShot(ArenaShot shot, Optional<Hit> hit){
		ArenaShot arenaShot = shot;
		Point2D arenaShotPos = new Point2D(arenaShot.getArenaX(), arenaShot.getArenaY());
		Point2D hitPos = null;
		int points = 0;

		if(hit.isPresent()) {
			hitPos = new Point2D(hit.get().getShot().getX(), hit.get().getShot().getY());
			//hitPos = new Point2D(hit.get().getImpactX(), hit.get().getImpactY());

			final TargetRegion r = hit.get().getHitRegion();
			if (r.tagExists("points")) {
				String pointsStr = r.getTag("points");
				points = Integer.parseInt(pointsStr);
			}
		}

		TrackedShot trackedShot = new TrackedShot(shot, target.getPosition(), arenaShotPos, hitPos, this.shotTime, points );
		trackedShots.add(trackedShot);
	}

	private void parMissed(){
		Platform.runLater(() -> {
			setShotTimerRowColor(Color.CORAL);
			final long drawShotLength = (System.currentTimeMillis() - roundStartTime); // s
			Shot fauxShot = new Shot(ShotColor.RED, 0.0,0.0, drawShotLength);
			this.getArenaPane().getCanvasManager().addShot(new DisplayShot(fauxShot, config.getMarkerRadius()), true);
		});

		setLength();
		setPoints(ShotColor.RED, "0");
		setLastTime("Par missed!");
	}

	protected void endRound(){
		if(round >= roundLimit){
			repeatExercise = false;
			pauseShotDetection(false);
			shootToReset = true;
			displayResults();
		}
	}

	private void displayResults(){

	}

	private void randomizeTargets(){
		logger.info(String.format("Target dimensions: w: %.1f, h: %.1f", target.getDimension().getWidth(), target.getDimension().getHeight()));
		final int maxX = (int) (super.getArenaWidth() - target.getDimension().getWidth() - 50);
		final int x = new Random().nextInt(maxX);

		final int maxY = (int) (super.getArenaHeight() - target.getDimension().getHeight() - 50);
		final int y = new Random().nextInt(maxY);

		logger.info(String.format("Placing target at x: %d, y: %d", x, y));
		target.setPosition(x, y);
	}


	private void drawShot(ArenaShot shot){
		Platform.runLater(() -> {
			Group canvasGroup = this.getArenaPane().getCanvasManager().getCanvasGroup();
			int shotIdx = canvasGroup.getChildren().indexOf(shot.getMarker());
			Ellipse marker = (Ellipse)this.getArenaPane().getCanvasManager().getCanvasGroup().getChildren().get(shotIdx);
			marker.setVisible(true);
		});
	}

	private void hideShots(){
		for (TrackedShot trackedShot : trackedShots) {
			Group canvasGroup = getArenaPane().getCanvasManager().getCanvasGroup();
			int shotIdx = canvasGroup.getChildren().indexOf(trackedShot.getArenaShot().getMarker());
			Ellipse marker = (Ellipse)canvasGroup.getChildren().get(shotIdx);
			marker.setVisible(false);
		}
	}

	private void hideTarget(){
		target.setVisible(false);
	}

	private void showTarget(){
		target.setVisible(true);
	}

	private void updateRoundLabel(){
		String roundText = String.format("Round: %d/%d", round, roundLimit);
		Platform.runLater(() -> roundLabel.setText(roundText));
	}

	private void hideLastTime(){
		Platform.runLater(()-> timeLabel.setVisible(false));
	}

	private void setLastTime(String time){
		Platform.runLater(() -> {
			timeLabel.setText(time);
			timeLabel.setVisible(true);
		});
	}

	private void removeRoundLabel(){
		Platform.runLater(() -> {
			if (this.getArenaPane() != null) {
				this.getArenaPane().getCanvasManager().getCanvasGroup().getChildren().remove(roundLabel);
			}
		});
	}

	private void removeTimeLabel(){
		Platform.runLater(() -> {
			if (this.getArenaPane() != null) {
				this.getArenaPane().getCanvasManager().getCanvasGroup().getChildren().remove(timeLabel);
			}
		});
	}

	private void startRoundTimer() {
		beepTime = System.currentTimeMillis();
		if(roundStartTime == 0) {
			roundStartTime = beepTime;
		}
	}

	@Override
	public void updatedDelayedStartInterval(int min, int max) {
		delayMin = min;
		delayMax = max;
	}

	private void resetValues() {
		shootToReset = false;
		score = 0;
		round = 0;
		showTextOnFeed("Score: 0");
		updateRoundLabel();
		hideLastTime();
		getParInterval(this);
	}

	private void setPoints(ShotColor shotColor, String points) {
		setShotTimerColumnText(POINTS_COL_NAME, points);

		if (shotColor.equals(ShotColor.RED) || shotColor.equals(ShotColor.INFRARED)) {
			score += Integer.parseInt(points);
		}

		String message = String.format("Score: %d", score);
		showTextOnFeed(message);
	}

	@Override
	public void updatedParInterval(double parTime) {
		this.parTime = parTime;
	}


	@Override
	public void targetUpdate(Target target, TargetChange change) {
		Point2D pos = target.getPosition();
		logger.info(String.format("Target position: x: %.1f, y: %.1f", pos.getX(), pos.getY()));
	}

	private void addRoundLimitExcersizePane(){
		LimitRoundsPane limitRoundsPane = new LimitRoundsPane(this);
		this.addExercisePane(limitRoundsPane);
	}

	@Override
	public void updateRoundLimit(Integer limit) {
		this.roundLimit = limit;
	}

	private static class TrackedShot {
		private ArenaShot arenaShot;
		float shotTime;
		int points;
		Point2D arenaShotPos;
		Point2D targetPos;
		Point2D hitPos;

		public TrackedShot(ArenaShot arenaShot, Point2D targetPos, Point2D arenaShotPos, Point2D hitPos, float shotTime, int points) {
			this.arenaShot = arenaShot;
			this.arenaShotPos = arenaShotPos;
			this.targetPos = targetPos;
			this.hitPos = hitPos;
			this.shotTime = shotTime;
			this.points = points;
		}

		public ArenaShot getArenaShot() {
			return arenaShot;
		}

		public float getShotTime() {
			return shotTime;
		}

		public int getPoints() {
			return points;
		}

		public Point2D getArenaShotPos() {
			return arenaShotPos;
		}

		public Point2D getTargetPos() {
			return targetPos;
		}

		public Point2D getHitPos() {
			return hitPos;
		}
	}


	private static class LimitRoundsPane extends GridPane {
		public LimitRoundsPane(RoundLimitListener listener) {
			getColumnConstraints().add(new ColumnConstraints(100));
			setVgap(5);

			final Label instructionsLabel = new Label("Set the number of shots per round.\n");
			instructionsLabel.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

			this.add(instructionsLabel, 0, 0, 2, 3);
			addRow(3, new Label("Shots per round"));

			final TextField limitField = new TextField(String.valueOf(MAX_ROUNDS));
			this.add(limitField, 1, 3);

			limitField.textProperty().addListener((observable, oldValue, newValue) -> {
				if (!newValue.matches("\\d*")) {
					limitField.setText(oldValue);
					limitField.positionCaret(limitField.getLength());
				} else {
					try {
						listener.updateRoundLimit(Integer.parseInt(limitField.getText()));
					} catch (NumberFormatException e){
						return;
					}
				}
			});
		}
	}
}
