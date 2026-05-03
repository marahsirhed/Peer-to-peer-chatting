package com.example.networks1project.network;

import com.example.networks1project.model.ClientHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class Server {

    private ServerSocket serverSocket;
    private final List<ClientHandler> connectedClients;
    private final AtomicBoolean running;

    public Server(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        this.connectedClients = new CopyOnWriteArrayList<>(); // Thread-safe list
        this.running = new AtomicBoolean(false);
    }

    public void setServerSocket(ServerSocket socket) throws IOException {
        this.serverSocket = socket;
    }

    public void startServer() {
        running.set(true);

        try {
            while (running.get() && !serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("New client has connected from " + socket.getInetAddress().getHostAddress());

                    ClientHandler clientHandler = new ClientHandler(socket);
                    connectedClients.add(clientHandler);

                    Thread thread = new Thread(clientHandler);
                    thread.setDaemon(true);
                    thread.start();
                } catch (SocketException e) {
                    // This is expected when closing the server socket
                    if (running.get()) {
                        System.err.println("Socket error: " + e.getMessage());
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            running.set(false);
            closeSererSocket();
        }
    }

    public void closeSererSocket() {
        running.set(false);
        try {
            // Disconnect all clients
            for (ClientHandler client : connectedClients) {
                try {
                    client.closeEverything(client.getSocket(), null, null);
                } catch (Exception e) {
                    // Ignore exceptions during shutdown
                }
            }
            connectedClients.clear();

            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Get the list of connected client handlers
    public List<ClientHandler> getConnectedClients() {
        return new ArrayList<>(connectedClients);
    }

    // Check if server is running
    public boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }
}