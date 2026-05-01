package com.simi.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * NIO Blocking Server using ServerSocketChannel and SocketChannel.
 * Despite using NIO classes, this server still blocks on accept() and read()
 * and spawns a new thread for each client (similar to traditional blocking IO).
 */
public class NioBlockingServer {

    public static void main(String[] args) throws IOException {
        System.out.println("Thread: " + Thread.currentThread().getName());

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        InetAddress inetAddress = InetAddress.getByName("0.0.0.0");
        SocketAddress socketAddress = new InetSocketAddress(inetAddress, 8080);
        serverSocketChannel.bind(socketAddress);

        System.out.println("NIO Blocking server is running on port 8080");

        try {
            while (true) {
                // blocking accept
                SocketChannel clientSocketChannel = serverSocketChannel.accept();

                new Thread(() -> handleClient(clientSocketChannel)).start();
            }
        } finally {
            serverSocketChannel.close();
        }
    }

    private static void handleClient(SocketChannel clientSocketChannel) {
        try {
            System.out.println("Thread: " + Thread.currentThread().getName());
            System.out.println("Connected to client: " + clientSocketChannel);

            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

            while (clientSocketChannel.read(byteBuffer) != -1) {
                byteBuffer.flip();
                String clientInput = new String(byteBuffer.array(), 0, byteBuffer.limit()).trim();

                int number = Integer.parseInt(clientInput);
                System.out.println("Client input: " + clientInput);
                if (number == 0) {
                    System.out.println("Client requested to close connection.");
                    break;
                }

                int square = number * number;
                String response = String.format("%d square is %d\r\n", number, square);

                byteBuffer.clear();
                byteBuffer.put(response.getBytes());

                byteBuffer.flip();
                clientSocketChannel.write(byteBuffer);

                byteBuffer.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
