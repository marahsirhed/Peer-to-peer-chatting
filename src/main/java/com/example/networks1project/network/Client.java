package com.example.networks1project.network;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String username;

    public Client(Socket socket, String username) {
        try {


            this.socket = socket;
            this.username = username;
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        } catch(IOException e) {
            closeEveryThing(socket, bufferedReader, bufferedWriter);
        }
    }

    public void sendMessage() {
        try {


            bufferedWriter.write(username);
            bufferedWriter.newLine();
            bufferedWriter.flush();

            Scanner scanner = new Scanner(System.in);

            while (socket.isConnected()) {
                String message = scanner.nextLine();
                bufferedWriter.write(username + ":" +message);
                bufferedWriter.newLine();
                bufferedWriter.flush();


            }

        } catch(IOException e) {
            closeEveryThing(socket, bufferedReader, bufferedWriter);
        }

    }

    public void listenForMessage() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String messageFromChat;

                while (socket.isConnected()) {
                    try {
                        messageFromChat = bufferedReader.readLine();
                        System.out.println(messageFromChat);
                    } catch(IOException e) {
                        closeEveryThing(socket, bufferedReader, bufferedWriter);

                     }
                }

            }
        }).start();
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

    public static void main(String[] args) throws IOException {

        Scanner scanner = new Scanner(System.in);
        System.out.println("Please enter your username: ");
        String username = scanner.nextLine();
        Socket socket = new Socket("localHost",1234);
        Client client = new Client(socket,username);

        client.listenForMessage();
        client.sendMessage();

    }




}
