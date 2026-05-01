package com.simi.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Demonstrates the Channel and Buffer concepts in Java NIO.
 * Copies bytes from source1.txt to source2.txt using FileChannel and ByteBuffer.
 */
public class ChannelBufferExample {

    public static void main(String[] args) {
        Path source1 = Paths.get("source1.txt");
        Path source2 = Paths.get("source2.txt");

        try (FileChannel channel1 = FileChannel.open(source1, StandardOpenOption.READ);
             FileChannel channel2 = FileChannel.open(source2, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {

            ByteBuffer buffer = ByteBuffer.allocate(3); // boat capacity = 3 bytes

            while (channel1.read(buffer) > 0) {
                buffer.flip();        // switch from write-mode to read-mode
                channel2.write(buffer); // write data to destination
                buffer.clear();       // switch back to write-mode, discard remaining data
            }

            System.out.println("File copied successfully using NIO Channel & Buffer.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
