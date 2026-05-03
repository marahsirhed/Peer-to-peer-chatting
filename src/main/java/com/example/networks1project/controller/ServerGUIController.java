package com.example.networks1project.controller;

import com.example.networks1project.model.ClientHandler;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.*;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import com.example.networks1project.network.Server;

public class ServerGUIController {

    @FXML private TextField portField;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private ListView<String> onlineUsersListView;
    @FXML private Label statusLabel, serverstartedlabel;
    @FXML private TextArea serverLogArea;
    @FXML private Label connectedClientsLabel;
    @FXML private Label upTimeLabel;

    // User management fields
    @FXML private TextField newUsernameField;
    @FXML private TextField newPasswordField;
    @FXML private Button addUserButton;
    @FXML private ListView<String> userListView;
    @FXML private Button deleteUserButton;

    private Server server;
    public ServerSocket serverSocket;
    private ObservableList<String> registeredUsers = FXCollections.observableArrayList();
    private ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private Timer userListUpdateTimer;
    private Timer upTimeTimer;
    private long serverStartTime;
    private boolean isServerRunning = false;

    @FXML
    public void initialize() {
        // Initialize ListView with observable list
        onlineUsersListView.setItems(onlineUsers);

        // Initialize user list
        if (userListView != null) {
            userListView.setItems(registeredUsers);
        }

        // Initially disable the stop button since server is not running
        stopButton.setDisable(true);
        if (serverLogArea != null) {
            serverLogArea.setEditable(false);
        }

        // Initialize labels
        if (connectedClientsLabel != null) {
            updateConnectedClientsCount(0);
        }

        if (upTimeLabel != null) {
            upTimeLabel.setText("Server Offline");
        }

        // Load existing users
        loadUsers();
    }

    private void loadUsers() {
        try {
            registeredUsers.clear();

            // Get the users.txt file location
            String usersFilePath = getUsersFilePath();
            if (usersFilePath == null) {
                log("users.txt file not found");
                return;
            }

            // Read users from file
            File usersFile = new File(usersFilePath);
            if (!usersFile.exists()) {
                log("Users file does not exist at: " + usersFilePath);
                return;
            }

            // Read all users from file
            try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        String[] parts = line.split(":");
                        if (parts.length == 2) {
                            registeredUsers.add(parts[0]);
                        }
                    }
                }
            }

            log("Loaded " + registeredUsers.size() + " users");
        } catch (IOException e) {
            log("Error loading users: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getUsersFilePath() {
        try {
            // Try to find users.txt in the resources folder
            URL resource = getClass().getClassLoader().getResource("users.txt");
            if (resource != null) {
                // Fix for Windows path issue - remove the leading slash and protocol part
                String path = resource.toString();
                if (path.startsWith("file:/")) {
                    // For Windows, remove the file:/ prefix
                    if (path.startsWith("file:/") && !path.startsWith("file:///")) {
                        path = "file:///" + path.substring(6);
                    }
                    try {
                        return new File(new URI(path)).getAbsolutePath();
                    } catch (URISyntaxException e) {
                        log("Error converting URL to file path: " + e.getMessage());
                    }
                }
            }

            // If not found or error occurred, create a new file in a temporary directory
            String tempDir = System.getProperty("java.io.tmpdir");
            File usersFile = new File(tempDir, "users.txt");

            if (!usersFile.exists()) {
                // Create default users file with some default accounts
                try (FileWriter fw = new FileWriter(usersFile);
                     BufferedWriter bw = new BufferedWriter(fw)) {
                    bw.write("alice:password");
                    bw.newLine();
                    bw.write("bob:password");
                    bw.newLine();
                    bw.write("john:password");
                    bw.newLine();
                }
                log("Created new users file at " + usersFile.getAbsolutePath());
            }

            return usersFile.getAbsolutePath();
        } catch (IOException e) {
            log("Error accessing users file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @FXML
    private void handleAddUser() {
        String username = newUsernameField.getText().trim();
        String password = newPasswordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Missing Information",
                    "Username and password cannot be empty.");
            return;
        }

        // Check if username already exists (case-insensitive)
        for (String existingUser : registeredUsers) {
            if (existingUser.equalsIgnoreCase(username)) {
                showAlert(Alert.AlertType.ERROR, "Error", "Duplicate Username",
                        "A user with this username already exists.");
                return;
            }
        }

        try {
            // Get the correct users.txt file path
            String usersFilePath = getUsersFilePath();
            if (usersFilePath == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "File Error",
                        "Could not access the users file.");
                return;
            }

            File usersFile = new File(usersFilePath);
            log("Writing to users file at: " + usersFile.getAbsolutePath());

            // Create backup of current file
            File backupFile = new File(usersFilePath + ".bak");
            copyFile(usersFile, backupFile);

            // Append to file
            try (FileWriter fw = new FileWriter(usersFile, true);
                 BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(username + ":" + password);
                bw.newLine();
                bw.flush(); // Ensure the data is flushed to disk
            }

            // Add to list and clear fields
            registeredUsers.add(username);
            newUsernameField.clear();
            newPasswordField.clear();

            log("Added new user: " + username);
            showAlert(Alert.AlertType.INFORMATION, "Success", "User Added",
                    "User " + username + " has been successfully added.");

        } catch (IOException e) {
            log("Error adding user: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to Add User",
                    "Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDeleteUser() {
        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No Selection",
                    "Please select a user to delete.");
            return;
        }

        // Confirm deletion
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Deletion");
        confirmAlert.setHeaderText("Delete User");
        confirmAlert.setContentText("Are you sure you want to delete user: " + selectedUser + "?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Get users file
                String usersFilePath = getUsersFilePath();
                if (usersFilePath == null) {
                    showAlert(Alert.AlertType.ERROR, "Error", "File Error",
                            "Could not access the users file.");
                    return;
                }

                File usersFile = new File(usersFilePath);

                // Read all users
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }

                // Create backup
                File backupFile = new File(usersFilePath + ".bak");
                copyFile(usersFile, backupFile);

                // Write all users except the deleted one
                try (FileWriter fw = new FileWriter(usersFile);
                     BufferedWriter bw = new BufferedWriter(fw)) {

                    for (String line : lines) {
                        String[] parts = line.split(":");
                        if (parts.length == 2 && !parts[0].equalsIgnoreCase(selectedUser)) {
                            bw.write(line);
                            bw.newLine();
                        }
                    }
                }

                // Remove from list
                registeredUsers.remove(selectedUser);

                log("Deleted user: " + selectedUser);
                showAlert(Alert.AlertType.INFORMATION, "Success", "User Deleted",
                        "User " + selectedUser + " has been deleted.");

            } catch (IOException e) {
                log("Error deleting user: " + e.getMessage());
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to Delete User",
                        "Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // Helper method to copy a file
    private void copyFile(File source, File dest) throws IOException {
        try (InputStream is = new FileInputStream(source);
             OutputStream os = new FileOutputStream(dest)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }

    @FXML
    private void handleStartServer() {
        // Validate the port field
        if (portField.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Port Required", "Please enter a valid port number.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portField.getText());
            if (port < 1024 || port > 65535) {
                showAlert(Alert.AlertType.ERROR, "Error", "Invalid Port", "Port must be between 1024 and 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Invalid Port", "Please enter a valid port number.");
            return;
        }

        try {
            log("Starting server on port " + portField.getText() + "...");

            serverSocket = new ServerSocket(port);
            server = new Server(serverSocket);
            Thread serverThread = new Thread(() -> {
                server.startServer();
            });
            serverThread.setDaemon(true);
            serverThread.start();

            isServerRunning = true;
            serverStartTime = System.currentTimeMillis();

            log("Server started successfully");
            serverstartedlabel.setVisible(true);
            serverstartedlabel.setText("Server running");
            serverstartedlabel.setTextFill(Color.GREEN);
            statusLabel.setText("Server running on port " + port);

            // Update UI state
            startButton.setDisable(true);
            stopButton.setDisable(false);
            portField.setDisable(true);

            // Start timers to update the online users list and uptime
            startUserListUpdateTimer();
            startUpTimeTimer();

        } catch (IOException e) {
            log("Error starting server: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Server Start Failed",
                    "Could not start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleStopServer() {
        if (!isServerRunning) {
            return;
        }

        log("Stopping server...");

        if (server != null) {
            server.closeSererSocket();
            server = null;
        }

        if (userListUpdateTimer != null) {
            userListUpdateTimer.cancel();
            userListUpdateTimer = null;
        }

        if (upTimeTimer != null) {
            upTimeTimer.cancel();
            upTimeTimer = null;
        }

        isServerRunning = false;
        onlineUsers.clear();
        updateConnectedClientsCount(0);
        upTimeLabel.setText("Server Offline");

        startButton.setDisable(false);
        stopButton.setDisable(true);
        portField.setDisable(false);
        statusLabel.setText("Server stopped");
        serverstartedlabel.setText("Server offline");
        serverstartedlabel.setTextFill(Color.RED);

        log("Server stopped");
        showAlert(Alert.AlertType.INFORMATION, "Success", "Server Stopped",
                "Server has been shut down successfully.");
    }

    private void startUserListUpdateTimer() {
        userListUpdateTimer = new Timer(true);
        userListUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateOnlineUsersList();
            }
        }, 0, 2000); // Update every 2 seconds
    }

    private void startUpTimeTimer() {
        upTimeTimer = new Timer(true);
        upTimeTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateUpTime();
            }
        }, 0, 1000); // Update every 1 second
    }

    private void updateOnlineUsersList() {
        Platform.runLater(() -> {
            onlineUsers.clear();
            int clientCount = ClientHandler.clientHandlers.size();

            for (ClientHandler client : ClientHandler.clientHandlers) {
                onlineUsers.add(client.getClientUsername() + " - " + client.getClientAddress());
            }

            updateConnectedClientsCount(clientCount);
        });
    }

    private void updateUpTime() {
        if (!isServerRunning) return;

        Platform.runLater(() -> {
            long upTimeMillis = System.currentTimeMillis() - serverStartTime;
            long seconds = upTimeMillis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;

            seconds %= 60;
            minutes %= 60;

            String upTimeText = String.format("Up Time: %02d:%02d:%02d", hours, minutes, seconds);
            upTimeLabel.setText(upTimeText);
        });
    }

    private void updateConnectedClientsCount(int count) {
        if (connectedClientsLabel != null) {
            connectedClientsLabel.setText("Connected Clients: " + count);
        }
    }

    private void log(String message) {
        Platform.runLater(() -> {
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
            String timestamp = dateFormat.format(new Date());
            serverLogArea.appendText("[" + timestamp + "] " + message + "\n");

            // Auto-scroll to bottom
            serverLogArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}