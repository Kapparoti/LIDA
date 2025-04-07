package org.lida.Interface;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

// Class used both in LIDAController and DirectoryDetails to animate the ".", ".." and "..." cycle in a label
public class LoadingAnimation {

	// --------------------- Variables ---------------------

	// Timeline used to call the update function
	private Timeline loadingAnimationTimeline = null;
	// Number of dots currently shown, decided only by the class, no setter
	private int animationDotCount;

	public int getAnimationDotCount() {
		return animationDotCount;
	}

	// Runnable specified by user to call at every update
	private final Runnable animationUpdate;

	// --------------------- Constructor ---------------------

	LoadingAnimation(Runnable animationUpdate) {
		this.animationUpdate = animationUpdate;
	}

	// --------------------- Class functions ---------------------

	// Starts the animation and initialize animationDotCount and loadingAnimationTimeline
	public void start() {
		stop();
		animationDotCount = 1;
		loadingAnimationTimeline = new Timeline(new KeyFrame(Duration.millis(500), _ -> update()));
		loadingAnimationTimeline.setCycleCount(Timeline.INDEFINITE);
		loadingAnimationTimeline.play();
		update();
	}

	// Update function, called by loadingAnimationTimeline
	private void update() {
		animationDotCount = (animationDotCount % 3) + 1;
		animationUpdate.run();
	}

	// Stops the animation and resets loadingAnimationTimeline
	public void stop() {
		if (loadingAnimationTimeline != null) {
			loadingAnimationTimeline.stop();
			loadingAnimationTimeline = null;
		}
	}
}
