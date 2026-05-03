package com.example.networks1project.model;

import java.util.ArrayList;
import java.util.List;

public class ArchiveManager {
    private static final List<String> archivedMessages = new ArrayList<>();

    public static void addMessage(String message) {
        archivedMessages.add(message);
    }

    public static List<String> getAllMessages() {
        return new ArrayList<>(archivedMessages);
    }

    public static void clear() {
        archivedMessages.clear();
    }
}
