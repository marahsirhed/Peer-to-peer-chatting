package com.example.networks1project.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {
    private final String sender;
    private final String content;
    private final LocalDateTime timestamp;

    public Message(String sender, String content) {
        this.sender = sender;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    public String getFormatted() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        return sender + ": " + content + "    [ " + timestamp.format(formatter) + " ]";
    }

    @Override
    public String toString() {
        return getFormatted();
    }

    public String getSender() { return sender; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
