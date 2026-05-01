package com.simi.nio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Client for the Non-blocking Server.
 * The client itself uses blocking SocketChannel for simplicity when reading from console.
 */
public class NonBlockingClient {

    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        InetAddress localhost = InetAddress.getByName("localhost");
        InetSocketAddress inetSocketAddress = new InetSocketAddress(localhost, 8080);

        socketChannel.connect(inetSocketAddress);
        System.out.println("Client info: " + socketChannel);

        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        try {
            while (true) {
                System.out.print("Enter a number (0 to exit): ");
                String userInputStr = consoleReader.readLine();

                if ("0".equals(userInputStr)) {
                    System.out.println("Close connection");
                    break;
                }

                byteBuffer.put(userInputStr.getBytes());
                byteBuffer.flip();
                socketChannel.write(byteBuffer);

                byteBuffer.clear();
                socketChannel.read(byteBuffer);

                byteBuffer.flip();
                System.out.println("Server response: " +
                        new String(byteBuffer.array(), 0, byteBuffer.limit()).trim());

                byteBuffer.clear();
            }
        } finally {
            socketChannel.close();
        }
    }
}
