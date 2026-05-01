package com.simi.blocking;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Traditional Blocking IO Server using java.net.ServerSocket.
 * Each client connection is handled by a new thread.
 */
public class BlockingServer {

    public static void main(String[] args) throws IOException {
        System.out.println("Thread: " + Thread.currentThread().getName());

        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Blocking server is running on port 8080");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from: " + clientSocket);

                // Spawn a new thread for each client
                new Thread(() -> handleClient(clientSocket)).start();
            }
        }
    }

    private static void handleClient(Socket clientSocket) {
        System.out.println("Thread: " + Thread.currentThread().getName());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Client input: " + line);

                int number = Integer.parseInt(line.trim());
                if (number == 0) {
                    System.out.println("Client requested to close connection.");
                    break;
                }

                int square = number * number;
                String response = String.format("%d square is %d", number, square);
                writer.println(response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
