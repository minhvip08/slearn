package com.simi.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Non-blocking Server using Java NIO Selector.
 * A single thread can handle multiple client connections concurrently.
 */
public class NonBlockingServer {

    public static void main(String[] args) throws IOException {
        System.out.println("Thread: " + Thread.currentThread().getName());

        InetAddress inetAddress = InetAddress.getByName("0.0.0.0");
        SocketAddress socketAddress = new InetSocketAddress(inetAddress, 8080);

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(socketAddress);
        serverSocketChannel.configureBlocking(false);

        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Non-blocking server is running on port 8080");

        while (true) {
            // blocking until there is at least one channel ready for an operation
            selector.select();

            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectionKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();

                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key);
                }

                keyIterator.remove(); // remove the key after processing
            }
        }
    }

    private static void handleAccept(SelectionKey key, Selector selector) throws IOException {
        System.out.println("Thread: " + Thread.currentThread().getName());

        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);

        // Register the new client channel with the selector to monitor READ events
        socketChannel.register(selector, SelectionKey.OP_READ);
        System.out.println("Accepted connection: " + socketChannel);
    }

    private static void handleRead(SelectionKey key) throws IOException {
        System.out.println("Thread: " + Thread.currentThread().getName());

        SocketChannel clientSocketChannel = (SocketChannel) key.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

        int read;
        while ((read = clientSocketChannel.read(byteBuffer)) != -1) {
            if (read == 0) {
                break;
            }

            byteBuffer.flip();
            String clientInput = new String(byteBuffer.array(), 0, byteBuffer.limit()).trim();

            int number = Integer.parseInt(clientInput);
            System.out.println("Client input: " + clientInput);

            if (number == 0) {
                System.out.println("Client requested to close connection.");
                clientSocketChannel.close();
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
    }
}
