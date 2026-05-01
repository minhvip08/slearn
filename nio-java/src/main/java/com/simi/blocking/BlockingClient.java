package com.simi.blocking;

import java.io.*;
import java.net.Socket;

/**
 * Traditional Blocking IO Client using java.net.Socket.
 */
public class BlockingClient {

    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket("localhost", 8080);
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Connected to server: " + socket);

            while (true) {
                System.out.print("Enter a number (0 to exit): ");
                String userInput = consoleReader.readLine();

                writer.println(userInput);

                if ("0".equals(userInput)) {
                    System.out.println("Close connection");
                    break;
                }

                String response = reader.readLine();
                System.out.println("Server response: " + response);
            }
        }
    }
}
