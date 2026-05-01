# Java NIO & Non-Blocking Server

> Reference article: [JavaNet p4 - Java NIO & Non-Blocking Server](https://viblo.asia/p/javanet-p4-java-nio-non-blocking-server-2oKLnn0GLQO)

## 1. Introduction

**Java NIO** (New Input/Output) was introduced as an alternative to the classic Java IO and Java Networking APIs. While Java IO is built around **streams**, Java NIO is built around **channels** and **buffers**, and it supports **non-blocking** operations.

Key components of Java NIO:
- **Channel**: A conduit for data transfer.
- **Buffer**: A container for data.
- **Selector**: A multiplexer that allows a single thread to manage multiple channels.

---

## 2. Channel & Buffer

### 2.1 Concept

In Java IO, everything starts and ends with a **Stream**. In Java NIO, everything starts and ends with a **Channel**.

Imagine your application is a **wood factory** located on a plain (no streams). To get wood from multiple sources, you dig a **channel** from the factory to each source. To transport the wood, you use a **boat** (Buffer). The bigger the boat, the more wood it can carry at once.

- **Channel**: Represents a connection to a data source (File, Socket, etc.).
- **Buffer**: A fixed-size container used to read from or write to a channel.

### 2.2 Reading from and Writing to Channels

Data is always read from a channel into a buffer, and written from a buffer to a channel:

```
Channel -> Buffer (read)
Buffer -> Channel (write)
```

### 2.3 Example: Copy Bytes with FileChannel

```java
Path source = Paths.get("source1.txt");
Path dest   = Paths.get("source2.txt");

try (FileChannel srcChannel = FileChannel.open(source, StandardOpenOption.READ);
     FileChannel dstChannel = FileChannel.open(dest, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {

    ByteBuffer buffer = ByteBuffer.allocate(3);

    while (srcChannel.read(buffer) > 0) {
        buffer.flip();   // switch buffer from write-mode to read-mode
        dstChannel.write(buffer);
        buffer.clear();  // switch buffer back to write-mode
    }
}
```

---

## 3. Buffer Internals

A `Buffer` is essentially a wrapper around a primitive array. It tracks three important properties:

| Property  | Description                                       |
|-----------|---------------------------------------------------|
| **Capacity** | Maximum amount of data the buffer can hold. Fixed at creation. |
| **Limit**    | In write mode: equals capacity. In read mode: equals the amount of data written. |
| **Position** | The index of the next element to be read or written. |

### 3.1 Key Methods

| Method      | Description |
|-------------|-------------|
| `flip()`    | Switches from **write-mode** to **read-mode** by setting `limit = position` and `position = 0`. |
| `clear()`   | Switches from **read-mode** to **write-mode** by setting `position = 0` and `limit = capacity`. It does **not** erase the underlying data. |
| `compact()` | Copies unread data to the beginning of the buffer and sets `position` right after it. Useful when you want to append new data without losing unread bytes. |
| `rewind()`  | Sets `position = 0` so you can re-read the buffer. |
| `mark()` / `reset()` | `mark()` saves the current `position`; `reset()` restores it. |
| `remaining()` | Returns `limit - position`. |
| `hasRemaining()` | Returns `true` if `position < limit`. |

---

## 4. Blocking IO Problem

Traditional Java networking (`ServerSocket` / `Socket`) uses **blocking IO**:

- `serverSocket.accept()` blocks until a client connects.
- `inputStream.read()` blocks until data is available.

To serve multiple clients concurrently, the server must spawn a **new thread per connection**. This approach has serious drawbacks:

- **High memory usage**: Each thread consumes memory (320KB on 32-bit JVM, 1MB on 64-bit JVM).
- **Scalability limit**: With 1,000,000 concurrent connections, you could need ~1TB of RAM just for threads.
- **Thread pool limits**: Using a thread pool avoids memory exhaustion, but if all threads are busy with long-running clients, new connections are rejected or wait indefinitely.

---

## 5. NIO Server (Still Blocking)

Java NIO provides `ServerSocketChannel` and `SocketChannel` as channel-based equivalents to `ServerSocket` and `Socket`.

By default, these channels operate in **blocking mode**, behaving almost identically to the old IO APIs. The following example still creates a new thread for every client:

```java
ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
serverSocketChannel.bind(new InetSocketAddress(8080));

while (true) {
    SocketChannel clientChannel = serverSocketChannel.accept(); // blocking
    new Thread(() -> handleClient(clientChannel)).start();
}
```

**Observation**: Simply replacing `ServerSocket` with `ServerSocketChannel` does **not** solve the scalability problem. We need **non-blocking mode**.

---

## 6. Non-Blocking IO

Java NIO allows channels to operate in **non-blocking mode**:

```java
SocketChannel socketChannel = SocketChannel.open();
socketChannel.configureBlocking(false);

ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
serverSocketChannel.configureBlocking(false);
```

In non-blocking mode:
- `read()` returns immediately. If no data is available, it returns `0`.
- `write()` returns immediately. If the channel is not ready, it may write `0` bytes.
- `accept()` returns immediately. If no connection is pending, it returns `null`.

This means a single thread can attempt to interact with many channels without getting stuck waiting on any single one.

---

## 7. Selector

A `Selector` is the core mechanism that enables a single thread to manage many channels efficiently. It monitors registered channels and informs the thread which ones are ready for I/O operations.

### 7.1 Analogy: The Restaurant Bell

In a blocking restaurant, one waiter stands at each table waiting for the customer to order.

In a non-blocking restaurant, customers sit at their tables and press a **bell** when they need service. One waiter can monitor many bells and attend only the tables that actually need attention.

The **Selector** is that bell system.

### 7.2 Registering Channels

A channel must be in **non-blocking mode** before registering with a `Selector`.

```java
Selector selector = Selector.open();

ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);
serverChannel.register(selector, SelectionKey.OP_ACCEPT);

SocketChannel socketChannel = SocketChannel.open();
socketChannel.configureBlocking(false);
socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
```

### 7.3 SelectionKey Interest Ops

| Operation      | Constant                     | Description                           |
|----------------|------------------------------|---------------------------------------|
| Accept         | `SelectionKey.OP_ACCEPT`     | Server socket is ready to accept a new connection. |
| Connect        | `SelectionKey.OP_CONNECT`    | Socket is ready to finish connection. |
| Read           | `SelectionKey.OP_READ`       | Channel has data ready to read.       |
| Write          | `SelectionKey.OP_WRITE`      | Channel is ready for writing.         |

### 7.4 Selecting Ready Channels

```java
// Blocking: waits until at least one channel is ready
int readyCount = selector.select();

// Non-blocking: returns immediately
int readyCount = selector.selectNow();
```

After selecting, retrieve the ready keys:

```java
Set<SelectionKey> selectedKeys = selector.selectedKeys();
Iterator<SelectionKey> it = selectedKeys.iterator();

while (it.hasNext()) {
    SelectionKey key = it.next();

    if (key.isAcceptable()) { /* accept new connection */ }
    if (key.isReadable())   { /* read data */ }
    if (key.isWritable())   { /* write data */ }
    if (key.isConnectable()){ /* finish connection */ }

    it.remove(); // important: remove the processed key
}
```

---

## 8. Non-Blocking Server with Selector

Putting it all together, we can build a server that uses **a single thread** to handle many clients:

```java
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.bind(new InetSocketAddress(8080));
serverChannel.configureBlocking(false);

Selector selector = Selector.open();
serverChannel.register(selector, SelectionKey.OP_ACCEPT);

while (true) {
    selector.select();

    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
    while (it.hasNext()) {
        SelectionKey key = it.next();

        if (key.isAcceptable()) {
            SocketChannel client = serverChannel.accept();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
        } else if (key.isReadable()) {
            SocketChannel client = (SocketChannel) key.channel();
            // read data, process, and optionally write back
        }

        it.remove();
    }
}
```

**Key observations when running this server:**
- The same thread handles `accept()` and `read()` for all clients.
- No new threads are created per connection.
- The server scales to thousands or millions of connections with minimal memory overhead.

---

## 9. Pros and Cons of Non-Blocking Servers

### Advantages
- **Resource efficiency**: One thread handles all connections. No 1MB-per-thread memory cost.
- **Scalability**: You can scale horizontally by adding more selector threads if one thread becomes a bottleneck.

### Disadvantages
- **Complex code**: Managing state across many connections in a single thread is harder than the simple "one thread per client" model.
- **CPU-bound tasks block the selector**: If processing a request takes too long, all other connections wait. Heavy work should be offloaded to worker threads.

---

## 10. Running the Examples

This project includes three server implementations:

1. **Blocking Server** (`com.simi.blocking.BlockingServer`)
2. **NIO Blocking Server** (`com.simi.nio.NioBlockingServer`)
3. **Non-Blocking Server** (`com.simi.nio.NonBlockingServer`)

And two clients:
1. **Blocking Client** (`com.simi.blocking.BlockingClient`)
2. **NIO Client** (`com.simi.nio.NioBlockingClient` / `com.simi.nio.NonBlockingClient`)

### Gradle commands

```bash
# Terminal 1: Run the server
./gradlew run -PmainClass=com.simi.nio.NonBlockingServer

# Terminal 2: Run a client
./gradlew run -PmainClass=com.simi.nio.NonBlockingClient
```

Or compile and run directly:

```bash
./gradlew compileJava
java -cp build/classes/java/main com.simi.nio.NonBlockingServer
java -cp build/classes/java/main com.simi.nio.NonBlockingClient
```

---

## 11. Summary

| Feature                | Java IO (Blocking)           | Java NIO (Non-Blocking)                |
|------------------------|------------------------------|----------------------------------------|
| Abstraction            | Stream                       | Channel + Buffer                       |
| I/O Mode               | Blocking                     | Blocking or Non-blocking               |
| Multiplexing           | One thread per client        | One thread for many clients (Selector) |
| Memory per connection  | ~1MB (thread stack)          | ~few KB (buffer)                       |
| Ease of use            | Simple                       | More complex                           |
| Scalability            | Limited                      | High                                   |

The most important takeaway: **Blocking IO requires many threads to handle many connections. Non-blocking IO can handle many connections with a single thread**, which is the foundation of high-performance servers like Netty, Node.js, and many modern web frameworks.
