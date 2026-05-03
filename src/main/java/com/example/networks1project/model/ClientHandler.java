package com.example.networks1project.model;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    // Thread-safe map to track username-to-clienthandler mapping
    private static Map<String, ClientHandler> usernameMap = new ConcurrentHashMap<>();

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String ClientUsername;
    private boolean running = true;

    public ClientHandler(Socket socket) {
        try {
            this.socket = socket;
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String loginInfo = bufferedReader.readLine(); // Read "username:password"
            if (processLogin(loginInfo)) {
                bufferedWriter.write("AUTH_SUCCESS");
                bufferedWriter.newLine();
                bufferedWriter.flush();

                // Check if username already exists in connected clients
                if (usernameMap.containsKey(ClientUsername)) {
                    // Remove old client handler with the same username
                    ClientHandler oldHandler = usernameMap.get(ClientUsername);
                    clientHandlers.remove(oldHandler);
                    try {
                        oldHandler.socket.close();
                    } catch (IOException e) {
                        // Ignore, just trying to clean up
                    }
                }

                // Add to client lists
                clientHandlers.add(this);
                usernameMap.put(ClientUsername, this);

                // Notify everyone about the new user
                broadCastMessage("Server: " + this.ClientUsername + " has entered the chat");
                broadCastMessage("NEW_USER|" + ClientUsername + ":" + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());

                // Send current user list to the new client
                sendExistingUsersList();

            } else {
                bufferedWriter.write("AUTH_FAILED");
                bufferedWriter.newLine();
                bufferedWriter.flush();
                closeEverything(socket, bufferedReader, bufferedWriter);
            }

        } catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter);
        }
    }

// Update ClientHandler.java run method to handle broadcast messages

    @Override
    public void run() {
        String messageFromClient;

        while (running && socket.isConnected()) {
            try {
                messageFromClient = bufferedReader.readLine();
                if (messageFromClient == null) {
                    // Client disconnected
                    break;
                }





                // Handle broadcast message
                if (messageFromClient.startsWith("BROADCAST_MESSAGE|")) {
                    // Format: BROADCAST_MESSAGE|sender|message
                    handleBroadcastMessage(messageFromClient);
                }
                else if (messageFromClient.startsWith("CLIENT_DISCONNECT|")) {
                    String username = messageFromClient.substring("CLIENT_DISCONNECT|".length());
                    System.out.println("Client " + username + " disconnecting properly");
                    break; // Exit the loop which will call removeClientHandler()
                }

                // Handle status change messages specially
                else if (messageFromClient.startsWith("STATUS_CHANGE|")) {
                    // Format: STATUS_CHANGE|username|status
                    String[] parts = messageFromClient.split("\\|");
                    if (parts.length == 3) {
                        String username = parts[1];
                        String status = parts[2];
                        System.out.println("Status change received: " + username + " -> " + status);
                        broadcastStatusChange(username, status);
                    } else {
                        broadCastMessage(messageFromClient);
                    }
                }
                else {
                    // Regular message broadcast
                    broadCastMessage(messageFromClient);
                }

            } catch (SocketException e) {
                // Socket was closed, break the loop
                break;
            } catch (IOException e) {
                closeEverything(socket, bufferedReader, bufferedWriter);
                break;
            }
        }

        // If we exit the loop, close the connection
        closeEverything(socket, bufferedReader, bufferedWriter);
    }

    private void handleBroadcastMessage(String rawMessage) {
        try {
            String[] parts = rawMessage.split("\\|", 3);
            if (parts.length == 3) {
                String sender = parts[1];
                String message = parts[2];

                // Create a broadcast message that all clients will understand
                String broadcastFormat = "BROADCAST|" + sender + "|" + message;

                // Send to all clients including the sender (for consistency)
                for (ClientHandler client : clientHandlers) {
                    if (client.running) { // Skip disconnected clients
                        try {
                            client.bufferedWriter.write(broadcastFormat);
                            client.bufferedWriter.newLine();
                            client.bufferedWriter.flush();
                        } catch (IOException e) {
                            // If error occurs for a specific client, close their connection
                            // but continue with other clients
                            client.closeEverything(client.socket, client.bufferedReader, client.bufferedWriter);
                        }
                    }
                }

                System.out.println("Broadcast from " + sender + ": " + message);
            }
        } catch (Exception e) {
            System.err.println("Error handling broadcast message: " + e.getMessage());
        }
    }

    // Helper function to process the login information
    private boolean processLogin(String loginInfo) {
        String[] parts = loginInfo.split(":");
        if (parts.length == 2) {
            this.ClientUsername = parts[0];  // Store the username
            String password = parts[1];  // Extract password
            return authenticateUser(this.ClientUsername, password);
        }
        return false; // Invalid format if split doesn't work
    }

    // Method to authenticate the username and password
// Method to authenticate the username and password
    private boolean authenticateUser(String username, String password) {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("users.txt");
            if (inputStream == null) {
                // Try to find users.txt in the temp directory as fallback
                String tempDir = System.getProperty("java.io.tmpdir");
                File usersFile = new File(tempDir, "users.txt");
                if (usersFile.exists()) {
                    inputStream = new FileInputStream(usersFile);
                    System.out.println("Using users.txt from temp dir: " + usersFile.getAbsolutePath());
                } else {
                    System.out.println("users.txt not found");
                    return false;
                }
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            boolean found = false;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String fileUsername = parts[0].trim();
                    String filePassword = parts[1].trim();

                    // Case-insensitive comparison for both username and password
                    if (fileUsername.equalsIgnoreCase(username) && filePassword.equalsIgnoreCase(password)) {
                        found = true;
                        break;
                    }
                }
            }

            reader.close();
            return found;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Send the list of all existing users to the newly connected client
    private void sendExistingUsersList() {
        try {
            for (ClientHandler client : clientHandlers) {
                if (!client.equals(this)) {  // Don't send the client their own info
                    String userInfo = "NEW_USER|" + client.ClientUsername + ":" +
                            client.socket.getInetAddress().getHostAddress() + ":" +
                            client.socket.getPort();
                    this.bufferedWriter.write(userInfo);
                    this.bufferedWriter.newLine();
                    this.bufferedWriter.flush();

                    // Add a small delay to prevent message congestion
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter);
        }
    }

    // Broadcast a user's status change
    public void broadcastStatusChange(String username, String status) {
        String statusMessage = "STATUS_CHANGE|" + username + "|" + status;
        broadCastMessage(statusMessage);
    }

    // Broadcasting messages to all clients
    public void broadCastMessage(String messageToSend) {
        for (ClientHandler client : clientHandlers) {
            try {
                if (!client.ClientUsername.equals(ClientUsername) && client.running) {
                    client.bufferedWriter.write(messageToSend);
                    client.bufferedWriter.newLine();
                    client.bufferedWriter.flush();
                }
            } catch (IOException e) {
                closeEverything(socket, bufferedReader, bufferedWriter);
            }
        }
    }

    // Remove client from the list and notify others
    public void removeClientHandler() {
        clientHandlers.remove(this);
        usernameMap.remove(ClientUsername);

        // Notify others that this user has left
        broadCastMessage("Server: " + this.ClientUsername + " has left the chat");
        // Also send a USER_DISCONNECTED message so clients can update their lists
        broadCastMessage("USER_DISCONNECTED|" + ClientUsername);

        System.out.println("Client " + ClientUsername + " disconnected");
    }

    // Close the connection and clean up resources
    public void closeEverything(Socket socket, BufferedReader in, BufferedWriter out) {
        running = false;
        removeClientHandler();

        try {
            if (in != null) {
                in.close();
            }

            if (out != null) {
                out.close();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Just log the error, don't propagate during cleanup
            e.printStackTrace();
        }
    }

    // Getter for username (to be used by ServerGUIController)
    public String getClientUsername() {
        return ClientUsername;
    }

    // Getter for client address (to be used by ServerGUIController)
    public String getClientAddress() {
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    // Getter for the socket
    public Socket getSocket() {
        return socket;
    }

    // Check if client is still connected
    public boolean isConnected() {
        return running && socket != null && socket.isConnected() && !socket.isClosed();
    }
}