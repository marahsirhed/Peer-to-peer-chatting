package com.example.networks1project.controller;

import com.example.networks1project.model.ArchiveManager;
import com.example.networks1project.model.FilePacket;
import com.example.networks1project.model.FileTransferStatistics;
import com.example.networks1project.model.Message;
import com.example.networks1project.network.FileTransferService;
import com.example.networks1project.network.UDPPeer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.prefs.Preferences;
// Import statements to add if needed
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.util.LinkedHashMap;
import java.util.Map;


public class UDPGUIController {

    @FXML private TextField LocalAddressField;
    @FXML private TextField LocalPortField;
    @FXML private TextField RemoteAdressField, passwordField;
    @FXML private TextField RemotePortField, ServerPortFeild, ServerIPFeild, UsernameField;
    @FXML private TextArea EnterTextArea;
    @FXML private Button SendButton, connectToServerButton, disconnectButton;
    @FXML private Button loginButton, logoutButton;
    @FXML private ListView<String> chatListView, onlineUsers;
    @FXML private Button deleteAllButton;
    @FXML private Button deleteButton;
    @FXML private Button archiveButton;
    @FXML private Label authenticationStatusLabel;
    @FXML private Button StartButton;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private Label lastLoginLabel;
    @FXML private Label statusLabel;
    @FXML private Button sendFileButton;
    @FXML private Button receiveFileButton;
    @FXML private Button selectDirButton;
    @FXML private Label receivingDirLabel;
    @FXML private ProgressBar fileTransferProgressBar;
    @FXML private TextArea fileTransferStatsArea;
    private FileTransferService fileTransferService;
    private int fileTransferPort = 9000; // Default file transfer port


    @FXML private ListView<TimedArchivedMessage> archiveTabList;
    @FXML private Button restoreFromTabButton;
    private final Map<String, TimedArchivedMessage> archivedMessages = new LinkedHashMap<>();

    @FXML
    private void restoreFromTab() {
        TimedArchivedMessage selected = archiveTabList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Warning", "No Selection", "Please select a message to restore.");
            return;
        }

        String original = selected.getOriginalMessage();
        archiveTabList.getItems().remove(selected);
        if (selected.getTimeline() != null) selected.getTimeline().stop();
        archivedMessages.remove(original);

        // Add back to chat
        chatListView.getItems().add(original);

        // Notify peer if connected
        if (udpPeer != null) {
            String messageContent = original.replace("You: ", "").replace("Remote: ", "").trim();
            udpPeer.sendMessage("RESTORE|" + messageContent);
        }
    }

    // Update the existing archiveMessage method to use the tab
    @FXML
    private void archiveMessage() {
        // The old separate window code isn't needed anymore
        int selectedIndex = chatListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex == -1) {
            showAlert("Warning", "No Selection", "Please select a message to archive.");
            return;
        }

        String selectedMessage = chatListView.getItems().get(selectedIndex);
        addMessageToArchive(selectedMessage);
        chatListView.getItems().remove(selectedIndex);
    }

    // New addMessageToArchive method for the integrated UI
    public void addMessageToArchive(String message) {
        if (!archivedMessages.containsKey(message)) {
            TimedArchivedMessage timed = new TimedArchivedMessage(message, 2 * 60);
            archivedMessages.put(message, timed);

            Platform.runLater(() -> {
                archiveTabList.getItems().add(timed);

                Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
                    timed.decrementSeconds();
                    if (timed.getSecondsRemaining() <= 0) {
                        Platform.runLater(() -> {
                            archiveTabList.getItems().remove(timed);
                            archivedMessages.remove(message);
                        });
                        timed.getTimeline().stop();
                    } else {
                        archiveTabList.refresh();
                    }
                }));

                timeline.setCycleCount(Timeline.INDEFINITE);
                timeline.play();
                timed.setTimeline(timeline);
            });
        }
    }



    private UDPPeer udpPeer;
    private static UDPGUIController instance;
    private boolean isConnectedToServer = false;
    private boolean isLoggedIn = false;

    // User status tracking
    private enum UserStatus {
        ACTIVE, BUSY, AWAY
    }

    private void initFileTransferService() {
        fileTransferService = new FileTransferService();

        // Set callbacks
        fileTransferService.setStatisticsCallback(this::updateFileTransferStats);
        fileTransferService.setStatusUpdateCallback(this::updateFileTransferStatus);
        fileTransferService.setErrorCallback(this::handleFileTransferError);
        fileTransferService.setFileReceivedCallback(this::handleFileReceived);

        // Start listening for file transfers
        fileTransferService.startReceiving(fileTransferPort);

        // Update receiving directory label
        updateReceivingDirLabel();
    }

    private UserStatus currentStatus = UserStatus.ACTIVE;
    private Timer inactivityTimer;
    private long lastActivityTime;
    private static final long INACTIVITY_THRESHOLD = 30000; // 30 seconds in milliseconds

    public UDPGUIController() {
        instance = this;
    }

    public static UDPGUIController getInstance() {
        return instance;
    }

    @FXML
    public void initialize() {




        instance = this;

        setLocalHostIPAddress();

        // Set up cell factory for chat messages
        chatListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("You:")) setStyle("-fx-text-fill: blue;");
                    else if (item.startsWith("Remote:")) setStyle("-fx-text-fill: orange;");
                    else setStyle("");
                }
            }
        });

        // Set up handling for online users selection
        onlineUsers.setOnMouseClicked(event -> {
            String selected = onlineUsers.getSelectionModel().getSelectedItem();
            if (selected != null) {
                String[] parts = selected.split(":");
                if (parts.length >= 3) {
                    RemoteAdressField.setText(parts[1]);
                    RemotePortField.setText(parts[2]);
                }
            }
        });

        if (archiveTabList != null) {
            archiveTabList.setCellFactory(listView -> new ListCell<TimedArchivedMessage>() {
                @Override
                protected void updateItem(TimedArchivedMessage item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        int sec = item.getSecondsRemaining();
                        String display = String.format("%s  [Time left: %02d:%02d]",
                                item.getOriginalMessage(), sec / 60, sec % 60);
                        setText(display);
                        setStyle("-fx-text-fill: #FF9F0A;");
                    }
                }
            });
        }

        // Set up status ComboBox
        if (statusComboBox != null) {
            statusComboBox.getItems().clear();
            statusComboBox.getItems().addAll("ACTIVE", "BUSY", "AWAY");
            statusComboBox.setValue("ACTIVE");
            statusComboBox.setOnAction(event -> {
                String selected = statusComboBox.getValue();
                if (selected != null) {
                    changeUserStatus(UserStatus.valueOf(selected));
                }
            });
        }

        // Setup activity listeners for away status detection
        setupActivityListeners();

        // Initial last login display
        if (lastLoginLabel != null) {
            lastLoginLabel.setText("Last login: Never");
        }

        // Start inactivity monitor
        startInactivityMonitor();

        // Initial UI state
        updateUIForLoginState(false);
        updateUIForConnectionState(false);
        authenticationStatusLabel.setText("");

        initFileTransferService();
    }

    private void setupActivityListeners() {
        // Create a handler that updates last activity time
        EventHandler<Event> activityHandler = event -> {
            if (isLoggedIn) {
                updateLastActivityTime();

                // If status was AWAY, change it back to ACTIVE
                if (currentStatus == UserStatus.AWAY) {
                    changeUserStatus(UserStatus.ACTIVE);
                }
            }
        };

        // Get the scene (need to do this after the scene is initialized)
        Platform.runLater(() -> {
            Scene scene = EnterTextArea.getScene();
            if (scene != null) {
                // Add event filters for mouse and key events
                scene.addEventFilter(MouseEvent.ANY, activityHandler);
                scene.addEventFilter(KeyEvent.ANY, activityHandler);
            }
        });
    }

    private void updateLastActivityTime() {
        lastActivityTime = System.currentTimeMillis();
    }

    private void startInactivityMonitor() {
        // Cancel any existing timer
        if (inactivityTimer != null) {
            inactivityTimer.cancel();
        }

        // Initialize the last activity time
        updateLastActivityTime();

        // Create new timer
        inactivityTimer = new Timer(true);
        inactivityTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkInactivity();
            }
        }, 5000, 5000); // Check every 5 seconds
    }

    private void checkInactivity() {
        if (!isLoggedIn || currentStatus == UserStatus.BUSY) {
            return; // Don't check if not logged in or manually set to busy
        }

        long currentTime = System.currentTimeMillis();
        long inactiveTime = currentTime - lastActivityTime;

        if (inactiveTime >= INACTIVITY_THRESHOLD && currentStatus != UserStatus.AWAY) {
            // If inactive for more than threshold and not already away
            Platform.runLater(() -> changeUserStatus(UserStatus.AWAY));
        }
    }

    private void changeUserStatus(UserStatus newStatus) {
        if (newStatus == currentStatus) {
            return;
        }

        currentStatus = newStatus;

        // Update combo box if it exists and doesn't match
        if (statusComboBox != null && !statusComboBox.getValue().equals(newStatus.name())) {
            statusComboBox.setValue(newStatus.name());
        }

        // Update status label if it exists
        if (statusLabel != null) {
            statusLabel.setText("Status: " + newStatus.name());
        }

        // If connected to server, broadcast status change
        if (isLoggedIn && isConnectedToServer && udpPeer != null) {
            String username = UsernameField.getText();
            udpPeer.sendMessage("STATUS_CHANGE|" + username + "|" + newStatus.name());
        }

        log("Status changed to: " + newStatus.name());
    }

    @FXML
    private void login() {
        // Validate username and password
        if (UsernameField.getText().trim().isEmpty() || passwordField.getText().trim().isEmpty()) {
            showAlert("Error", "Login Failed", "Username and password are required.");
            return;
        }

        String username = UsernameField.getText().trim();
        String password = passwordField.getText().trim();

        // Attempt to authenticate with server-side users.txt file
        if (authenticateUser(username, password)) {
            isLoggedIn = true;

            // Record login time
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentLoginTime = dateFormat.format(new Date());

            // Get previous login time
            String previousLogin = loadLastLoginTime(username);

            // Save current login time
            saveLastLoginTime(username, currentLoginTime);

            // Display previous login time or "First login" message
            if (previousLogin != null && !previousLogin.isEmpty()) {
                lastLoginLabel.setText("Last login: " + previousLogin);
            } else {
                lastLoginLabel.setText("First login");
            }

            updateUIForLoginState(true);
            authenticationStatusLabel.setText("Logged in as: " + username);
            authenticationStatusLabel.setStyle("-fx-text-fill: green;");

            // Initialize status tracking
            updateLastActivityTime();
            currentStatus = UserStatus.ACTIVE;
            if (statusComboBox != null) {
                statusComboBox.setValue("ACTIVE");
            }

            log("Successfully logged in as: " + username);
            startSessionTimer();
        } else {
            showAlert("Error", "Login Failed", "Invalid username or password.");
            log("Failed login attempt for user: " + username);
        }
    }
    private void setLocalHostIPAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                // Skip loopback interfaces like 127.0.0.1
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    // Use only IPv4 addresses for simplicity
                    if (address.getHostAddress().contains(".")) {
                        LocalAddressField.setText(address.getHostAddress());
                        return;
                    }
                }
            }
        } catch (SocketException e) {
            // Fallback to localhost if there's an error
            log("Error detecting host IP address: " + e.getMessage());
        }
    }




    private boolean authenticateUser(String username, String password) {
        try {
            // First try to get the file from the users.txt resource
            InputStream inputStream = getUsersFileStream();
            if (inputStream == null) {
                log("Could not access users.txt file");
                return false;
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
                        log("User authenticated: " + username);
                        break;
                    }
                }
            }

            reader.close();
            if (!found) {
                log("Authentication failed for: " + username);
            }
            return found;
        } catch (IOException e) {
            e.printStackTrace();
            log("Error reading users file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Helper method to get access to the users.txt file
     * Works with both resource files and files in the temp directory
     */
    private InputStream getUsersFileStream() {
        try {
            // First try to get the file from the users.txt resource
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("users.txt");

            // If resource not found, try to access from a file in the temp directory
            if (inputStream == null) {
                String tempDir = System.getProperty("java.io.tmpdir");
                File usersFile = new File(tempDir, "users.txt");
                if (usersFile.exists()) {
                    log("Found users.txt file in temp directory: " + usersFile.getAbsolutePath());
                    return new FileInputStream(usersFile);
                } else {
                    // Create a default users file if not found
                    try (FileWriter fw = new FileWriter(usersFile);
                         BufferedWriter bw = new BufferedWriter(fw)) {
                        bw.write("alice:password");
                        bw.newLine();
                        bw.write("bob:password");
                        bw.newLine();
                        bw.write("john:password");
                        bw.newLine();
                    }
                    log("Created default users.txt file: " + usersFile.getAbsolutePath());
                    return new FileInputStream(usersFile);
                }
            } else {
                log("Found users.txt file in resources");
                return inputStream;
            }
        } catch (IOException e) {
            log("Error accessing users file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Add this to UDPGUIController.java

    @FXML private CheckBox sendToAllCheckBox;

    @FXML
    private void sendMessage() {
        String text = EnterTextArea.getText().trim();
        if (text.isEmpty() || udpPeer == null) {
            showAlert("Error", "Cannot Send", "Message is empty or not connected.");
            return;
        }

        String timestamp = java.time.LocalTime.now().withNano(0).toString();

        if (sendToAllCheckBox.isSelected() && isConnectedToServer) {
            // Send to all connected clients through the server
            try {
                // Format: BROADCAST_MESSAGE|sender|message
                String broadcastMessage = "BROADCAST_MESSAGE|" + UsernameField.getText() + "|" + text;

                // Send through the TCP connection to server
                udpPeer.sendToServer(broadcastMessage);

                // Add to local chat
                chatListView.getItems().add("You (to all): " + text + " [" + timestamp + "]");
            } catch (Exception e) {
                showAlert("Error", "Broadcast Failed", "Failed to send message to all users: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Regular P2P message
            Message message = new Message("You", text);
            udpPeer.sendMessage("MESSAGE|" + text);
            chatListView.getItems().add("You: " + text + " [" + timestamp + "]");
        }

        EnterTextArea.clear();
        updateLastActivityTime();
    }


    private void saveLastLoginTime(String username, String loginTime) {
        try {
            Preferences prefs = Preferences.userNodeForPackage(UDPGUIController.class);
            prefs.put("lastLogin_" + username.toLowerCase(), loginTime);
            prefs.flush(); // Ensure the preferences are saved immediately
            log("Saved login time for " + username + ": " + loginTime);
        } catch (Exception e) {
            log("Error saving login time: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String loadLastLoginTime(String username) {
        try {
            Preferences prefs = Preferences.userNodeForPackage(UDPGUIController.class);
            String lastLogin = prefs.get("lastLogin_" + username.toLowerCase(), "");
            log("Loaded last login time for " + username + ": " + (lastLogin.isEmpty() ? "Never" : lastLogin));
            return lastLogin;
        } catch (Exception e) {
            log("Error loading login time: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    @FXML
    private void logout() {
        // If connected to server, disconnect first
        if (isConnectedToServer) {
            disconnect();
        }

        if (sessionTimer != null) {
            sessionTimer.cancel();
            sessionTimer = null;
        }

        // Reset elapsed time label
        if (timeElapsedLabel != null) {
            timeElapsedLabel.setText("Session time: 00:00:00");
        }

        isLoggedIn = false;
        updateUIForLoginState(false);
        authenticationStatusLabel.setText("");
        lastLoginLabel.setText("Last login: Never");
        UsernameField.clear();
        passwordField.clear();
    }

    @FXML
    private void startReceiver() {
        // Validate input fields
        if (!validateFields(LocalAddressField, LocalPortField, RemoteAdressField, RemotePortField)) {
            showAlert("Error", "Missing Information", "Please fill in all required P2P connection fields.");
            return;
        }

        String localIP = LocalAddressField.getText();
        int localPort = Integer.parseInt(LocalPortField.getText());
        String remoteIP = RemoteAdressField.getText();
        int remotePort = Integer.parseInt(RemotePortField.getText());

        try {
            if (udpPeer == null) {
                udpPeer = new UDPPeer(localIP, localPort);
            }
            udpPeer.setRemoteAddress(remoteIP);
            udpPeer.setRemotePort(remotePort);
            udpPeer.setMessageHandler(this::handleIncomingMessage);
            udpPeer.startListening();

            // Update UI
            StartButton.setDisable(true);
            SendButton.setDisable(false);
            deleteButton.setDisable(false);
            deleteAllButton.setDisable(false);
            showAlert("Success", "Chat Started", "P2P chat connection established.");
        } catch (Exception e) {
            showAlert("Error", "Connection Failed", "Failed to start P2P connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Add to UDPGUIController.java

    @FXML private Button exportChatButton;

    @FXML
    private void exportChatHistory() {
        if (chatListView.getItems().isEmpty()) {
            showAlert("Warning", "Empty Chat", "There are no messages to export.");
            return;
        }

        // Create file chooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Chat History");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        // Set initial filename with timestamp
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());
        String defaultFilename = "chat_history_" + timestamp + ".txt";
        fileChooser.setInitialFileName(defaultFilename);

        // Show save dialog
        Stage stage = (Stage) exportChatButton.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (FileWriter writer = new FileWriter(file);
                 BufferedWriter bufferedWriter = new BufferedWriter(writer)) {

                // Write header
                bufferedWriter.write("Chat History - Exported on " +
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                bufferedWriter.newLine();
                bufferedWriter.write("------------------------------------------------");
                bufferedWriter.newLine();
                bufferedWriter.newLine();

                // Write all messages
                for (String message : chatListView.getItems()) {
                    bufferedWriter.write(message);
                    bufferedWriter.newLine();
                }



                // Write footer
                bufferedWriter.newLine();
                bufferedWriter.write("------------------------------------------------");
                bufferedWriter.newLine();
                bufferedWriter.write("End of Chat History - " + chatListView.getItems().size() + " messages");

                showAlert("Success", "Export Complete",
                        "Chat history has been successfully exported to:\n" + file.getAbsolutePath());

            } catch (IOException e) {
                showAlert("Error", "Export Failed", "Failed to export chat history: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @FXML private Label timeElapsedLabel;
    private Timer sessionTimer;
    private long loginTime;

    // Add this to the login method after successful authentication
    private void startSessionTimer() {
        // Store login time
        loginTime = System.currentTimeMillis();

        // Cancel any existing timer
        if (sessionTimer != null) {
            sessionTimer.cancel();
        }

        // Create and start new timer
        sessionTimer = new Timer(true);
        sessionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateSessionTime();
            }
        }, 0, 1000); // Update every second
    }

    private void updateSessionTime() {
        if (!isLoggedIn) return;

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - loginTime;

        // Convert to hours, minutes, seconds
        long seconds = elapsedTime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds %= 60;
        minutes %= 60;

        // Format time string
        final String timeString = String.format("Session time: %02d:%02d:%02d", hours, minutes, seconds);

        // Update UI on JavaFX thread
        Platform.runLater(() -> {
            if (timeElapsedLabel != null) {
                timeElapsedLabel.setText(timeString);
            }
        });
    }



    private void handleIncomingMessage(String rawMessage) {
        Platform.runLater(() -> {
            System.out.println("Received message: " + rawMessage);

            if (rawMessage.startsWith("BROADCAST|")) {
                // Format: BROADCAST|sender|message
                String[] parts = rawMessage.split("\\|", 3);
                if (parts.length == 3) {
                    String sender = parts[1];
                    String messageContent = parts[2];
                    String timestamp = java.time.LocalTime.now().withNano(0).toString();

                    // Format broadcast message differently
                    String formattedMessage = "[Broadcast] " + sender + ": " + messageContent + " [" + timestamp + "]";
                    chatListView.getItems().add(formattedMessage);

                    // Update activity time
                    updateLastActivityTime();
                }
            }

            if (rawMessage.startsWith("NEW_USER|")) {
                String userInfo = rawMessage.substring("NEW_USER|".length()).trim();
                String username = userInfo.split(":")[0];

                // Remove any existing entries for this username to avoid duplicates
                onlineUsers.getItems().removeIf(item -> {
                    String[] parts = item.split(":");
                    if (parts.length > 0) {
                        String itemUsername = parts[0];
                        return itemUsername.equals(username);
                    }
                    return false;
                });

                // Add the new user info with ACTIVE status by default
                onlineUsers.getItems().add(userInfo + " (ACTIVE)");

            } else if (rawMessage.startsWith("USER_DISCONNECTED|")) {
                String username = rawMessage.substring("USER_DISCONNECTED|".length()).trim();

                // Remove the disconnected user from the list
                onlineUsers.getItems().removeIf(userInfo -> {
                    String[] parts = userInfo.split(":");
                    if (parts.length > 0) {
                        String itemUsername = parts[0];
                        return itemUsername.equals(username);
                    }
                    return false;
                });

            } else if (rawMessage.startsWith("STATUS_CHANGE|")) {
                // Format: STATUS_CHANGE|username|status
                String[] parts = rawMessage.substring("STATUS_CHANGE|".length()).trim().split("\\|");
                if (parts.length == 2) {
                    String username = parts[0];
                    String newStatus = parts[1];

                    // Update status in online users list
                    updateUserStatus(username, newStatus);
                }

            } else if (rawMessage.startsWith("DELETE_MESSAGE|")) {
                String toDelete = rawMessage.substring("DELETE_MESSAGE|".length()).trim();
                for (String item : new ArrayList<>(chatListView.getItems())) {
                    String normalized = item.replace("You: ", "").replace("Remote: ", "").trim();
                    if (normalized.equals(toDelete)) {
                        chatListView.getItems().remove(item);
                        ArchiveController.addMessageToArchive(item);
                        break;
                    }
                }
            } else if (rawMessage.startsWith("CLEAR")) {
                List<String> messages = new ArrayList<>(chatListView.getItems());
                chatListView.getItems().clear();
                messages.forEach(ArchiveController::addMessageToArchive);
            } else if (rawMessage.startsWith("MESSAGE|")) {
                String messageContent = rawMessage.substring("MESSAGE|".length()).trim();
                String timestamp = java.time.LocalTime.now().withNano(0).toString();
                String messageWithTimestamp = "Remote: " + messageContent + " [" + timestamp + "]";
                chatListView.getItems().add(messageWithTimestamp);

                // Update activity time when receiving messages
                updateLastActivityTime();
            } else if (rawMessage.startsWith("RESTORE|")) {
                String messageContent = rawMessage.substring("RESTORE|".length()).trim();
                chatListView.getItems().add("Remote: " + messageContent);
            }
        });
    }

    private void updateUserStatus(String username, String status) {
        // Find user in the list and update their status
        for (int i = 0; i < onlineUsers.getItems().size(); i++) {
            String userInfo = onlineUsers.getItems().get(i);
            String[] parts = userInfo.split(":");

            if (parts.length > 0 && parts[0].equals(username)) {
                // Remove any existing status
                String baseInfo = userInfo;
                if (baseInfo.contains(" (")) {
                    baseInfo = baseInfo.substring(0, baseInfo.indexOf(" ("));
                }

                // Add new status
                String updatedInfo = baseInfo + " (" + status + ")";
                onlineUsers.getItems().set(i, updatedInfo);

                log("User " + username + " status changed to " + status);
                break;
            }
        }
    }

    @FXML
    public void Connect() throws IOException {
        if (!isLoggedIn) {
            showAlert("Error", "Not Logged In", "Please log in before connecting to the server.");
            return;
        }

        // Validate required fields
        if (!validateFields(ServerIPFeild, ServerPortFeild, LocalAddressField, LocalPortField)) {
            showAlert("Error", "Missing Information", "Please fill in all server and local connection fields.");
            return;
        }

        try {
            // Clear the online users list first
            onlineUsers.getItems().clear();

            Socket socket = new Socket(ServerIPFeild.getText(), Integer.parseInt(ServerPortFeild.getText()));
            String username = UsernameField.getText();
            String password = passwordField.getText();

            if (udpPeer == null) {
                String localIP = LocalAddressField.getText();
                int localPort = Integer.parseInt(LocalPortField.getText());
                udpPeer = new UDPPeer(localIP, localPort);
            }

            boolean authenticated = udpPeer.setConnection(socket, username, password);
            if (authenticated) {
                isConnectedToServer = true;
                updateUIForConnectionState(true);

                authenticationStatusLabel.setText("Connected as: " + username);
                authenticationStatusLabel.setStyle("-fx-text-fill: green;");

                // Start listening for messages, including user list updates
                udpPeer.setMessageHandler(this::handleIncomingMessage);
                udpPeer.startListening();

                // Add ourselves to the online users list (locally only)
                Platform.runLater(() -> {
                    String localUserInfo = username + ":" + LocalAddressField.getText() + ":" + LocalPortField.getText() + " (You)";

                    // Remove any existing "You" entries
                    onlineUsers.getItems().removeIf(item -> item.contains("(You)"));

                    // Add the current user
                    onlineUsers.getItems().add(localUserInfo);
                });

                // Broadcast initial status
                if (statusComboBox != null) {
                    udpPeer.sendMessage("STATUS_CHANGE|" + username + "|" + statusComboBox.getValue());
                }

                showAlert("Success", "Connected", "Successfully connected to the server.");
            } else {
                authenticationStatusLabel.setText("Authentication failed");
                authenticationStatusLabel.setStyle("-fx-text-fill: red;");
                showAlert("Error", "Authentication Failed", "Invalid username or password.");
            }
        } catch (IOException e) {
            authenticationStatusLabel.setText("Connection failed");
            authenticationStatusLabel.setStyle("-fx-text-fill: red;");
            showAlert("Error", "Connection Failed", "Could not connect to server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void disconnect() {
        if (udpPeer != null) {
            udpPeer.disconnect(); // Call the new disconnect method instead of just stop()
            udpPeer = null;
        }

        isConnectedToServer = false;
        updateUIForConnectionState(false);
        onlineUsers.getItems().clear();
        authenticationStatusLabel.setText("Logged in as: " + UsernameField.getText());
        showAlert("Info", "Disconnected", "Successfully disconnected from the server.");
    }

    @FXML
    private void deleteMessage() {
        int selectedIndex = chatListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex == -1) {
            showAlert("Warning", "No Selection", "Please select a message to delete.");
            return;
        }

        String selectedMessage = chatListView.getItems().get(selectedIndex);
        // Use our local archive instead of ArchiveController
        addMessageToArchive(selectedMessage);
        chatListView.getItems().remove(selectedIndex);

        // If connected to remote peer, notify them of deletion
        if (udpPeer != null) {
            String messageToDelete = selectedMessage.replace("You: ", "").replace("Remote: ", "").trim();
            udpPeer.sendMessage("DELETE_MESSAGE|" + messageToDelete);
        }
    }

    @FXML
    private void deleteAllmessages() {
        if (chatListView.getItems().isEmpty()) {
            showAlert("Warning", "No Messages", "There are no messages to delete.");
            return;
        }

        List<String> allMessages = new ArrayList<>(chatListView.getItems());
        chatListView.getItems().clear();

        for (String msg : allMessages) {
            // Use our local archive instead of ArchiveController
            addMessageToArchive(msg);

            if (udpPeer != null) {
                String messageToDelete = msg.replace("You: ", "").replace("Remote: ", "").trim();
                udpPeer.sendMessage("DELETE_MESSAGE|" + messageToDelete);
            }
        }

        showAlert("Info", "Messages Deleted", "All messages have been deleted and archived.");
    }





    public void restoreMessageToChat(String rawMessage) {
        chatListView.getItems().add("You: " + rawMessage);
        if (udpPeer != null) {
            udpPeer.sendMessage("RESTORE|" + rawMessage);
        }
    }

    public void stop() {
        if (udpPeer != null) {
            udpPeer.stop();
        }

        if (inactivityTimer != null) {
            inactivityTimer.cancel();
        }

        if (sessionTimer != null) {
            sessionTimer.cancel();
        }

        if (fileTransferService != null) {
            fileTransferService.shutdown();
        }
    }

    @FXML
    private void handleSendFile() {
        if (!isConnectedToServer || udpPeer == null) {
            showAlert("Error", "Not Connected", "You must be connected to the server to send files.");
            return;
        }

        String remoteIP = RemoteAdressField.getText();
        if (remoteIP == null || remoteIP.trim().isEmpty()) {
            showAlert("Error", "No Recipient", "Please select a recipient from the online users list.");
            return;
        }

        // Show file chooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        Stage stage = (Stage) sendFileButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            // Get remote IP from the selection
            try {
                fileTransferService.sendFile(remoteIP, fileTransferPort, selectedFile);

                // Show starting message
                fileTransferStatsArea.clear();
                fileTransferStatsArea.appendText("Starting file transfer of: " + selectedFile.getName() + "\n");
                fileTransferStatsArea.appendText("Size: " + formatFileSize(selectedFile.length()) + "\n");
                fileTransferStatsArea.appendText("Recipient: " + remoteIP + "\n");
                fileTransferStatsArea.appendText("Please wait...\n");

                // Reset progress bar
                fileTransferProgressBar.setProgress(0);

            } catch (Exception e) {
                showAlert("Error", "Transfer Failed", "Failed to initiate file transfer: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    // Select receiving directory button action
    @FXML
    private void handleSelectReceivingDir() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Receiving Directory");

        // Set initial directory to current receiving directory
        File currentDir = new File(fileTransferService.getReceivingDirectory());
        if (currentDir.exists() && currentDir.isDirectory()) {
            dirChooser.setInitialDirectory(currentDir);
        }

        Stage stage = (Stage) selectDirButton.getScene().getWindow();
        File selectedDir = dirChooser.showDialog(stage);

        if (selectedDir != null) {
            fileTransferService.setReceivingDirectory(selectedDir.getAbsolutePath());
            updateReceivingDirLabel();
        }
    }

    // Update the receiving directory label
    private void updateReceivingDirLabel() {
        if (receivingDirLabel != null) {
            receivingDirLabel.setText("Receiving files in: " + fileTransferService.getReceivingDirectory());
        }
    }

    // Callback for file transfer statistics updates
    private void updateFileTransferStats(FileTransferStatistics stats) {
        Platform.runLater(() -> {
            // Update text area with statistics
            fileTransferStatsArea.clear();
            fileTransferStatsArea.appendText(stats.toString());

            // Update progress bar if transfer is in progress
            double progress = (double) stats.getPacketsTransferred() / stats.getTotalPackets();
            fileTransferProgressBar.setProgress(progress);

            // If transfer is complete, show finished message
            if (stats.getEndTime() > 0) {
                fileTransferStatsArea.appendText("\n\nTransfer complete!");
            }
        });
    }

    // Callback for file transfer status updates
    private void updateFileTransferStatus(FilePacket packet) {
        // This can be used for more detailed progress updates if needed
    }

    // Callback for file transfer errors
    private void handleFileTransferError(Exception e) {
        Platform.runLater(() -> {
            showAlert("Error", "File Transfer Error", e.getMessage());
            fileTransferStatsArea.appendText("\n\nERROR: " + e.getMessage());
        });
    }

    // Callback for file received event
    private void handleFileReceived(File file) {
        Platform.runLater(() -> {
            showAlert("Success", "File Received",
                    "File received and saved to:\n" + file.getAbsolutePath());
        });
    }

    // Helper to format file size
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    public void restoreFromArchive(String message) {
        chatListView.getItems().add(message);
        if (udpPeer != null) {
            udpPeer.sendMessage("RESTORE|" + message.replace("You: ", "").replace("Remote: ", "").trim());
        }
    }

    // Helper methods

    private boolean validateFields(TextField... fields) {
        for (TextField field : fields) {
            if (field.getText().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void showAlert(String type, String title, String message) {
        Alert alert;

        switch (type) {
            case "Error":
                alert = new Alert(Alert.AlertType.ERROR);
                break;
            case "Warning":
                alert = new Alert(Alert.AlertType.WARNING);
                break;
            case "Success":
                alert = new Alert(Alert.AlertType.INFORMATION);
                break;
            default:
                alert = new Alert(Alert.AlertType.INFORMATION);
        }

        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateUIForLoginState(boolean isLoggedIn) {
        // Update UI elements based on login state
        loginButton.setDisable(isLoggedIn);
        logoutButton.setDisable(!isLoggedIn);
        UsernameField.setDisable(isLoggedIn);
        passwordField.setDisable(isLoggedIn);

        // If not logged in, disable connection
        connectToServerButton.setDisable(!isLoggedIn);

        // Enable/disable status controls
        if (statusComboBox != null) {
            statusComboBox.setDisable(!isLoggedIn);
        }
    }

    private void updateUIForConnectionState(boolean isConnected) {
        // Update UI elements based on connection state
        connectToServerButton.setDisable(isConnected);
        disconnectButton.setDisable(!isConnected);
        ServerIPFeild.setDisable(isConnected);
        ServerPortFeild.setDisable(isConnected);
    }

    private void log(String message) {
        System.out.println("[Client] " + message);
    }
}