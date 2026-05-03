package com.example.networks1project.network;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class UDPPeer {

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String username;

    private DatagramSocket datagramSocket;
    private boolean listening;

    private String localAddress;
    private int localPort;

    private String remoteAddress;
    private int remotePort;

    private Consumer<String> messageHandler;

    public UDPPeer(String localAddress, int localPort) {
        this.localAddress = localAddress;
        this.localPort = localPort;

    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }
    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    // Add this method to UDPPeer.java

    public void sendToServer(String message) throws IOException {
        if (socket != null && socket.isConnected() && bufferedWriter != null) {
            bufferedWriter.write(message);
            bufferedWriter.newLine();
            bufferedWriter.flush();
        } else {
            throw new IOException("Not connected to server");
        }
    }




    public boolean setConnection(Socket socket, String username, String password){
        try {
            this.socket = socket;
            this.username = username;
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            bufferedWriter.write(username + ":" + password);
            bufferedWriter.newLine();
            bufferedWriter.flush();

            String response = bufferedReader.readLine();

            if ("AUTH_SUCCESS".equals(response)) {
                // Start a separate thread to continuously read messages from the server
                Thread readerThread = new Thread(() -> {
                    try {
                        String incomingMessage;
                        while (socket.isConnected() && (incomingMessage = bufferedReader.readLine()) != null) {
                            // Process messages from the server
                            if (messageHandler != null) {
                                final String message = incomingMessage;
                                messageHandler.accept(message);
                            }
                        }
                    } catch (IOException e) {
                        closeEveryThing(socket, bufferedReader, bufferedWriter);
                    }
                });

                readerThread.setDaemon(true);
                readerThread.start();

                return true;
            }
            return false;
        } catch(IOException e) {
            closeEveryThing(socket, bufferedReader, bufferedWriter);
            return false;
        }
    }



    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    public void startListening() {
        try {
            datagramSocket = new DatagramSocket(localPort);
            listening = true;

            Thread listenerThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (listening) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        datagramSocket.receive(packet);
                        String message = new String(packet.getData(), 0, packet.getLength());

                        if (messageHandler != null) {
                            messageHandler.accept(message);
                        }

                    } catch (Exception e) {
                        if (listening) e.printStackTrace();
                    }
                }
            });

            listenerThread.setDaemon(true);
            listenerThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        try {
            if (datagramSocket == null || datagramSocket.isClosed()) return;

            InetAddress ip = InetAddress.getByName(remoteAddress);
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), ip, remotePort);
            datagramSocket.send(packet);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void closeEveryThing(Socket socket, BufferedReader in, BufferedWriter out) {
        try {

            if (socket != null) {
                socket.close();
            }

            if (in != null) {
                in.close();
            }

            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        listening = false;
        if (datagramSocket != null && !datagramSocket.isClosed()) {
            datagramSocket.close();
        }
    }
    public void disconnect() {
        if (socket != null && bufferedWriter != null) {
            try {
                // Send a disconnect message to server to notify about the proper disconnect
                bufferedWriter.write("CLIENT_DISCONNECT|" + username);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            } catch (IOException e) {
                // Ignore, we're disconnecting anyway
            }
            closeEveryThing(socket, bufferedReader, bufferedWriter);
        }
    }

    // 🚧 Future TCP integration comment:
    // Consider creating TCPPeer or TCPServer classes in a similar fashion
    // to abstract away protocol-level operations cleanly.
}
