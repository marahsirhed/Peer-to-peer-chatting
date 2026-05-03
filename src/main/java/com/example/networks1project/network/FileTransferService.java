package com.example.networks1project.network;

import com.example.networks1project.model.FilePacket;
import com.example.networks1project.model.FileTransferStatistics;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class FileTransferService {
    private static final int PACKET_SIZE = 8192; // 8KB packet size
    private static final int FILE_TRANSFER_PORT = 9000; // Default port for file transfer

    private ExecutorService executorService;
    private ServerSocket serverSocket;
    private boolean isListening = false;

    private Consumer<FileTransferStatistics> statisticsCallback;
    private Consumer<FilePacket> statusUpdateCallback;
    private Consumer<Exception> errorCallback;
    private Consumer<File> fileReceivedCallback;

    private String receivingDirectory = "C:\\"; // Default receiving directory

    public FileTransferService() {
        this.executorService = Executors.newCachedThreadPool();

        // Set the default receiving directory to user's documents folder or home directory
        String userHomeDir = System.getProperty("user.home");
        String documentsPath = userHomeDir + File.separator + "Documents";
        File documentsDir = new File(documentsPath);

        if (documentsDir.exists() && documentsDir.isDirectory() && documentsDir.canWrite()) {
            this.receivingDirectory = documentsPath;
        } else {
            // Fallback to user's home directory if Documents folder isn't accessible
            this.receivingDirectory = userHomeDir;
        }
    }

    // Set callbacks for progress and statistics
    public void setStatisticsCallback(Consumer<FileTransferStatistics> callback) {
        this.statisticsCallback = callback;
    }

    public void setStatusUpdateCallback(Consumer<FilePacket> callback) {
        this.statusUpdateCallback = callback;
    }

    public void setErrorCallback(Consumer<Exception> callback) {
        this.errorCallback = callback;
    }

    public void setFileReceivedCallback(Consumer<File> callback) {
        this.fileReceivedCallback = callback;
    }

    public void setReceivingDirectory(String directory) {
        if (directory != null && !directory.isEmpty()) {
            File dir = new File(directory);
            if (dir.exists() && dir.isDirectory() && dir.canWrite()) {
                this.receivingDirectory = directory;
            }
        }
    }

    public String getReceivingDirectory() {
        return receivingDirectory;
    }

    // Start listening for incoming file transfers
    public void startReceiving(int port) {
        if (isListening) {
            return;
        }

        executorService.submit(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isListening = true;
                System.out.println("File transfer service listening on port " + port);

                while (isListening) {
                    try {
                        Socket socket = serverSocket.accept();
                        System.out.println("Incoming file transfer from " + socket.getInetAddress());
                        handleIncomingFileTransfer(socket);
                    } catch (IOException e) {
                        if (isListening) { // Only report if not intentionally closed
                            if (errorCallback != null) {
                                errorCallback.accept(e);
                            }
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                if (errorCallback != null) {
                    errorCallback.accept(e);
                }
                e.printStackTrace();
            }
        });
    }

    // Stop listening for incoming transfers
    public void stopReceiving() {
        isListening = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Send a file to a remote peer
    public void sendFile(String remoteHost, int port, File file) {
        if (file == null || !file.exists() || !file.isFile() || !file.canRead()) {
            if (errorCallback != null) {
                errorCallback.accept(new IOException("Invalid or unreadable file: " + file));
            }
            return;
        }

        // Initialize statistics
        FileTransferStatistics stats = new FileTransferStatistics(file.getName(), file.length());

        executorService.submit(() -> {
            Socket socket = null;
            try {
                // Connect to receiver
                socket = new Socket(remoteHost, port);
                sendFileOverSocket(socket, file, stats);
            } catch (Exception e) {
                if (errorCallback != null) {
                    errorCallback.accept(e);
                }
                e.printStackTrace();
            } finally {
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    // Handle an incoming file transfer
    private void handleIncomingFileTransfer(Socket socket) {
        executorService.submit(() -> {
            FileTransferStatistics stats = null;

            try {
                // Set up streams
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

                // Get initial packet with file info
                FilePacket firstPacket = (FilePacket) in.readObject();

                if (firstPacket.getType() != FilePacket.PacketType.FILE_INFO) {
                    throw new IOException("Invalid file transfer protocol");
                }

                // Create statistics object
                stats = new FileTransferStatistics(firstPacket.getFileName(), firstPacket.getFileSize());
                stats.setTotalPackets(
                        (long) Math.ceil((double) firstPacket.getFileSize() / PACKET_SIZE));

                // Prepare file
                File outputFile = new File(receivingDirectory, firstPacket.getFileName());

                // If file exists, add timestamp to filename
                if (outputFile.exists()) {
                    String newName = firstPacket.getFileName().replaceFirst("[.][^.]+$", "") +
                            "_" + System.currentTimeMillis() +
                            firstPacket.getFileName().substring(
                                    firstPacket.getFileName().lastIndexOf("."));
                    outputFile = new File(receivingDirectory, newName);
                }

                // Send acknowledgment
                FilePacket ack = new FilePacket(FilePacket.PacketType.ACK, 0);
                out.writeObject(ack);

                // Start receiving file data
                try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                    while (true) {
                        FilePacket packet = (FilePacket) in.readObject();

                        if (packet.getType() == FilePacket.PacketType.COMPLETE) {
                            break;
                        }

                        if (packet.getType() == FilePacket.PacketType.DATA) {
                            if (packet.getData() != null) {
                                fileOut.write(packet.getData());
                                stats.recordPacketReceived(packet.getTimestamp());

                                // Update status
                                if (statusUpdateCallback != null) {
                                    statusUpdateCallback.accept(packet);
                                }

                                // Send acknowledgment
                                FilePacket dataAck = new FilePacket(FilePacket.PacketType.ACK,
                                        packet.getPacketId());
                                out.writeObject(dataAck);
                            }
                        }
                    }
                }

                // File transfer complete
                stats.completeTransfer();

                // Notify that file was received
                if (fileReceivedCallback != null) {
                    fileReceivedCallback.accept(outputFile);
                }

                // Update statistics
                if (statisticsCallback != null) {
                    statisticsCallback.accept(stats);
                }

            } catch (Exception e) {
                if (errorCallback != null) {
                    errorCallback.accept(e);
                }
                e.printStackTrace();

                // Update statistics on error
                if (stats != null && statisticsCallback != null) {
                    stats.completeTransfer();
                    statisticsCallback.accept(stats);
                }
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // Helper method to send a file over an established socket
// Helper method to send a file over an established socket
    private void sendFileOverSocket(Socket socket, File file, FileTransferStatistics stats)
            throws IOException, ClassNotFoundException {

        // Set up streams
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        // Send file info packet
        FilePacket infoPacket = new FilePacket(FilePacket.PacketType.FILE_INFO, 0);
        infoPacket.setFileName(file.getName());
        infoPacket.setFileSize(file.length());
        out.writeObject(infoPacket);

        // Calculate total packets
        long totalPackets = (long) Math.ceil((double) file.length() / PACKET_SIZE);
        stats.setTotalPackets(totalPackets);

        // Wait for acknowledgment
        FilePacket ack = (FilePacket) in.readObject();
        if (ack.getType() != FilePacket.PacketType.ACK) {
            throw new IOException("Protocol error: expected ACK");
        }

        // Send file data
        try (FileInputStream fileIn = new FileInputStream(file)) {
            byte[] buffer = new byte[PACKET_SIZE];
            int bytesRead;
            long packetId = 1;

            while ((bytesRead = fileIn.read(buffer)) > 0) {
                // Create data packet
                FilePacket dataPacket = new FilePacket(FilePacket.PacketType.DATA, packetId++);

                // If read less than buffer size, create appropriately sized array
                byte[] data = bytesRead < buffer.length
                        ? Arrays.copyOf(buffer, bytesRead)
                        : buffer;

                dataPacket.setData(data);

                // Send packet
                out.writeObject(dataPacket);

                // Record packet sent
                stats.recordPacketReceived(dataPacket.getTimestamp());

                // Update status
                if (statusUpdateCallback != null) {
                    statusUpdateCallback.accept(dataPacket);
                }

                // Wait for acknowledgment
                FilePacket dataAck = (FilePacket) in.readObject();
                if (dataAck.getType() != FilePacket.PacketType.ACK) {
                    throw new IOException("Protocol error: expected ACK");
                }
            }

            // Send completion packet
            FilePacket completePacket = new FilePacket(FilePacket.PacketType.COMPLETE, packetId);
            out.writeObject(completePacket);

            // Complete statistics
            stats.completeTransfer();

            // Update statistics
            if (statisticsCallback != null) {
                statisticsCallback.accept(stats);
            }
        }
    }

    // Shutdown the service
    public void shutdown() {
        stopReceiving();
        executorService.shutdown();
    }
}