package com.example.networks1project.model;

import java.io.Serializable;
import java.util.Arrays;

public class FilePacket implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum PacketType {
        FILE_INFO,     // Initial packet with file information
        DATA,          // Data packet
        ACK,           // Acknowledgment
        COMPLETE,      // Transfer complete
        ERROR          // Error occurred
    }

    private PacketType type;
    private long packetId;       // Sequence number for ordering and tracking
    private long fileSize;       // Total file size in bytes
    private String fileName;     // Original file name
    private byte[] data;         // Data payload
    private long timestamp;      // Timestamp for jitter calculation
    private String errorMessage; // Used with ERROR type

    public FilePacket(PacketType type, long packetId) {
        this.type = type;
        this.packetId = packetId;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public PacketType getType() { return type; }
    public void setType(PacketType type) { this.type = type; }

    public long getPacketId() { return packetId; }
    public void setPacketId(long packetId) { this.packetId = packetId; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @Override
    public String toString() {
        return "FilePacket{" +
                "type=" + type +
                ", packetId=" + packetId +
                ", fileSize=" + fileSize +
                ", fileName='" + fileName + '\'' +
                ", dataSize=" + (data != null ? data.length : 0) +
                ", timestamp=" + timestamp +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
