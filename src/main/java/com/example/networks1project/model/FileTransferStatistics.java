
// File Transfer Statistics Class
package com.example.networks1project.model;

import java.util.ArrayList;
import java.util.List;

public class FileTransferStatistics {
    private String fileName;
    private long fileSize;
    private long totalPackets;
    private long packetsTransferred;
    private long startTime;
    private long endTime;
    private List<Long> packetDelays = new ArrayList<>();

    // For calculating jitter
    private List<Long> interPacketDelays = new ArrayList<>();
    private Long lastPacketArrivalTime = null;

    public FileTransferStatistics(String fileName, long fileSize) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.startTime = System.currentTimeMillis();
    }

    public synchronized void recordPacketReceived(long packetSendTime) {
        long now = System.currentTimeMillis();
        long delay = now - packetSendTime;
        packetDelays.add(delay);
        packetsTransferred++;

        // Calculate inter-packet delay for jitter
        if (lastPacketArrivalTime != null) {
            long interPacketDelay = now - lastPacketArrivalTime;
            interPacketDelays.add(interPacketDelay);
        }
        lastPacketArrivalTime = now;
    }

    public void completeTransfer() {
        endTime = System.currentTimeMillis();
    }

    public long getTotalTimeMillis() {
        return (endTime > 0) ? (endTime - startTime) : (System.currentTimeMillis() - startTime);
    }

    public double getAveragePacketDelayMillis() {
        if (packetDelays.isEmpty()) return 0;
        return packetDelays.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public double getJitterMillis() {
        if (interPacketDelays.size() < 2) return 0;

        // Calculate mean deviation of inter-packet delays
        double mean = interPacketDelays.stream().mapToLong(Long::longValue).average().orElse(0);
        double sumOfSquaredDifferences = 0;

        for (Long delay : interPacketDelays) {
            double difference = delay - mean;
            sumOfSquaredDifferences += difference * difference;
        }

        return Math.sqrt(sumOfSquaredDifferences / interPacketDelays.size());
    }

    public long getMaxPacketDelayMillis() {
        if (packetDelays.isEmpty()) return 0;
        return packetDelays.stream().mapToLong(Long::longValue).max().orElse(0);
    }

    public long getMinPacketDelayMillis() {
        if (packetDelays.isEmpty()) return 0;
        return packetDelays.stream().mapToLong(Long::longValue).min().orElse(0);
    }

    public double getTransferRateMBps() {
        long time = getTotalTimeMillis();
        if (time <= 0) return 0;
        return (fileSize / 1024.0 / 1024.0) / (time / 1000.0);
    }

    // Getters
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public long getTotalPackets() { return totalPackets; }
    public void setTotalPackets(long totalPackets) { this.totalPackets = totalPackets; }
    public long getPacketsTransferred() { return packetsTransferred; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }

    @Override
    public String toString() {
        return "File Transfer Statistics:\n" +
                "- File: " + fileName + "\n" +
                "- Size: " + formatFileSize(fileSize) + "\n" +
                "- Packets: " + packetsTransferred + "/" + totalPackets + "\n" +
                "- Transfer Time: " + formatTime(getTotalTimeMillis()) + "\n" +
                "- Transfer Rate: " + String.format("%.2f MB/s", getTransferRateMBps()) + "\n" +
                "- Avg Packet Delay: " + String.format("%.2f ms", getAveragePacketDelayMillis()) + "\n" +
                "- Jitter: " + String.format("%.2f ms", getJitterMillis());
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;

        return String.format("%d:%02d.%03d", minutes, seconds, millis % 1000);
    }
}