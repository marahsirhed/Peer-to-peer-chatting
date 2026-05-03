package com.example.networks1project.controller;

import com.example.networks1project.controller.TimedArchivedMessage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;

public class ArchiveController {

    @FXML private ListView<TimedArchivedMessage> archiveList;
    @FXML private Button closeButton;
    @FXML private Button restoreButton;
    @FXML private Label timerLabel;

    private static final Map<String, TimedArchivedMessage> archivedMap = new LinkedHashMap<>();
    private static Stage stage;

    public static void addMessageToArchive(String message) {
        if (!archivedMap.containsKey(message)) {
            TimedArchivedMessage timed = new TimedArchivedMessage(message, 2 * 60);
            archivedMap.put(message, timed);
        }
    }

    public void setStage(Stage s) {
        stage = s;
    }

    @FXML
    public void initialize() {
        archiveList.getItems().clear();
        archiveList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(TimedArchivedMessage item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    int sec = item.getSecondsRemaining();
                    String display = String.format("%s  [Time left: %02d:%02d]",
                            item.getOriginalMessage(), sec / 60, sec % 60);
                    setText(display);
                }
            }
        });

        for (TimedArchivedMessage msg : archivedMap.values()) {
            archiveList.getItems().add(msg);

            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
                msg.decrementSeconds();
                if (msg.getSecondsRemaining() <= 0) {
                    archiveList.getItems().remove(msg);
                    archivedMap.remove(msg.getOriginalMessage());
                    msg.getTimeline().stop();
                } else {
                    archiveList.refresh();
                }
            }));

            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();
            msg.setTimeline(timeline);
        }
    }

    @FXML
    void restoreArchivedMessage() {
        TimedArchivedMessage selected = archiveList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String original = selected.getOriginalMessage();
        archiveList.getItems().remove(selected);
        if (selected.getTimeline() != null) selected.getTimeline().stop();
        archivedMap.remove(original);

        if (original.contains("you:")) {
            original = original.replace("you:", "remote:");
        } else if (original.contains("remote:")) {
            original = original.replace("remote:", "you:");
        }
        // Restore to chat list and notify peer
        UDPGUIController.getInstance().restoreFromArchive(original);
    }



    @FXML
    void closeArchiveWindow() {
        if (stage != null) stage.close();
    }
}
