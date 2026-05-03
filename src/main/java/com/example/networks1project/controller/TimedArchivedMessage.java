package com.example.networks1project.controller;

import javafx.animation.Timeline;

public class TimedArchivedMessage {
    private final String originalMessage;
    private int secondsRemaining;
    private Timeline timeline;

    public TimedArchivedMessage(String message, int seconds) {
        this.originalMessage = message;
        this.secondsRemaining = seconds;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public void decrementSeconds() {
        secondsRemaining--;
    }

    public void setTimeline(Timeline timeline) {
        this.timeline = timeline;
    }

    public Timeline getTimeline() {
        return timeline;
    }
}
