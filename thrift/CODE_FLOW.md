# Giải Thích Flow Code - Apache Thrift Calculator Project

> Tài liệu này mô tả chi tiết luồng thực thi của project Thrift Calculator trong workspace, từ `.thrift` definition đến RPC call hoàn chỉnh giữa Client và Server.

---

## 1. Cấu Trúc Project

```
thrift/
├── thrift/
│   ├── tutorial.thrift         # Định nghĩa Calculator service + Work struct
│   ├── shared.thrift           # Định nghĩa SharedService + SharedStruct
│   └── multiplication.thrift   # Định nghĩa MultiplicationService
├── src/
│   └── main/java/com/simi/
│       ├── CalculatorHandler.java   # Business logic implementation
│       ├── JavaServer.java          # Server bootstrap
│       └── JavaClient.java          # Client bootstrap
├── build/
│   └── generated-sources/
│       └── thrift/
│           ├── tutorial/            # Code generated từ tutorial.thrift
│           │   ├── Calculator.java
│           │   ├── Work.java
│           │   ├── Operation.java
│           │   └── InvalidOperation.java
│           ├── shared/              # Code generated từ shared.thrift
│           │   ├── SharedService.java
│           │   └── SharedStruct.java
│           └── learn/               # Code generated từ multiplication.thrift
│               └── MultiplicationService.java
└── build.gradle
```

---

## 2. Flow Tổng Quát: `.thrift` → Generated Code → Runtime

```
┌─────────────────┐     thrift -gen java      ┌──────────────────┐
│  .thrift files  │  ──────────────────────►  │  Generated Java  │
│  (IDL definitions)│                          │  (build/generated-sources/thrift/)      │
└─────────────────┘                            └──────────────────┘
                                                        │
                                                        ▼
┌─────────────────┐     implements                ┌──────────────────┐
│  CalculatorHandler│ ◄──────────────────────────  │  Calculator.Iface│
│  (Developer code)│                              │  (Generated)      │
└─────────────────┘                              └──────────────────┘
        │                                                 │
        │ serve()                                         │ new Client(protocol)
        ▼                                                 ▼
┌─────────────────┐                            ┌──────────────────┐
│   JavaServer    │ ◄──── RPC over socket ──── │   JavaClient      │
│  (TServer)      │                            │  (TTransport +    │
└─────────────────┘                            │   TProtocol)      │
                                               └──────────────────┘
```

---

## 3. Server Flow

### 3.1 Khởi động Server (`JavaServer.java`)

```java
public static void main(String[] args) {
    handler = new CalculatorHandler();               // 1. Tạo handler
    processor = new Calculator.Processor(handler);   // 2. Tạo processor
    
    new Thread(() -> simple(processor)).start();     // 3. Start simple server
    new Thread(() -> secure(processor)).start();     // 4. Start SSL server
}
```

**Bước 1: Handler**
- `CalculatorHandler` implements `Calculator.Iface` (interface sinh bởi Thrift).
- Đây là **duy nhất** code do developer viết - chứa business logic.

**Bước 2: Processor**
- `Calculator.Processor` được sinh bởi Thrift compiler.
- Nhận `handler` vào constructor.
- Implement `TProcessor.process(TProtocol in, TProtocol out)`.

**Bước 3 & 4: Server Threads**
- Mỗi thread chạy một `TServer` riêng biệt.

### 3.2 Simple Server (Port 9090)

```java
TServerTransport serverTransport = new TServerSocket(9090);
TServer server = new TSimpleServer(
    new Args(serverTransport).processor(processor)
);
server.serve();
```

Flow bên trong `TSimpleServer.serve()`:

```
1. serverTransport.listen()         // Bind socket
2. serverTransport.accept()         // Chấp nhận client connection
3. TProtocol in = inputFactory.getProtocol(clientTransport)
4. TProtocol out = outputFactory.getProtocol(clientTransport)
5. processor.process(in, out)       // Xử lý RPC
6. Lặp lại từ bước 2 (cho đến khi đóng)
```

### 3.3 Secure Server (Port 9091)

```java
TSSLTransportParameters params = new TSSLTransportParameters();
params.setKeyStore("src/main/resources/.keystore", "thrift", null, null);
TServerTransport serverTransport = TSSLTransportFactory.getServerSocket(9091, 0, null, params);
```

- Sử dụng `TSSLTransportFactory` để wrap TCP socket trong SSL/TLS.
- Server dùng **keystore** chứa private key để xác thực.

---

## 4. RPC Processing Flow (Bên trong Processor)

Khi một client gửi request `add(1, 1)`:

```
Client                              Server
  │                                    │
  │ ── Message: "add", seqid, args ──► │
  │                                    │
  │        Processor.process()         │
  │              │                     │
  │              ▼                     │
  │        readMessageBegin()          │
  │              │                     │
  │              ▼                     │
  │        processMap_.get("add")      │
  │              │                     │
  │              ▼                     │
  │        add.process(seqid, in, out) │
  │              │                     │
  │              ├── read add_args     │
  │              │   (num1=1, num2=1)  │
  │              ▼                     │
  │        handler.add(1, 1)           │
  │              │                     │
  │              ├── returns 2         │
  │              ▼                     │
  │        write add_result(success=2) │
  │              │                     │
  │              ▼                     │
  │        writeMessageEnd()           │
  │                                    │
  │ ◄──────── Result: 2 ────────────── │
```

### Chi tiết từng method trong CalculatorHandler:

#### `ping()`
```java
public void ping() {
    System.out.println("ping()");  // Chỉ in log, không return gì
}
```
- Client gọi `send_ping()` rồi `recv_ping()` (chờ response empty).

#### `add(int n1, int n2)`
```java
public int add(int n1, int n2) {
    return n1 + n2;
}
```
- Server nhận `add_args` struct, đọc 2 fields `num1` và `num2`.
- Trả về `add_result` struct với field `success` = 2.

#### `calculate(int logid, Work work)`
```java
public int calculate(int logid, Work work) throws InvalidOperation {
    switch (work.op) {
        case ADD:       return work.num1 + work.num2;
        case SUBTRACT:  return work.num1 - work.num2;
        case MULTIPLY:  return work.num1 * work.num2;
        case DIVIDE:
            if (work.num2 == 0) {
                InvalidOperation io = new InvalidOperation();
                io.whatOp = work.op.getValue();
                io.why = "Cannot divide by 0";
                throw io;  // Throw exception về client
            }
            return work.num1 / work.num2;
    }
    // Lưu kết quả vào log HashMap
    log.put(logid, entry);
}
```

- Nếu `num2 == 0` và `op == DIVIDE`, server throw `InvalidOperation`.
- Exception được serialize và gửi về client.
- Nếu thành công, kết quả được lưu vào `HashMap<Integer, SharedStruct> log`.

#### `getStruct(int key)` (từ `shared.SharedService`)
```java
public SharedStruct getStruct(int key) {
    return log.get(key);  // Trả về entry đã lưu từ calculate()
}
```
- Client gọi sau `calculate()` để kiểm tra log.

#### `zip()` (oneway)
```java
public void zip() {
    System.out.println("zip()");
}
```
- Client chỉ gửi request, **không đợi** response.
- Trong generated code, `send_zip()` được gọi nhưng **không có** `recv_zip()`.

---

## 5. Client Flow

### 5.1 Khởi tạo Connection

```java
// Simple mode
TTransport transport = new TSocket("localhost", 9090);
transport.open();

// Secure mode
TSSLTransportFactory.TSSLTransportParameters params = 
    new TSSLTransportFactory.TSSLTransportParameters();
params.setTrustStore("src/main/resources/.truststore", "thrift", "SunX509", "JKS");
TTransport transport = TSSLTransportFactory.getClientSocket("localhost", 9091, 0, params);
```

- Simple: Dùng `TSocket` thông thường.
- Secure: Dùng `TSSLTransportFactory` với **truststore** chứa certificate tin cậy.

### 5.2 Tạo Protocol và Client Stub

```java
TProtocol protocol = new TBinaryProtocol(transport);
Calculator.Client client = new Calculator.Client(protocol);
```

- `TBinaryProtocol`: Encode/decode theo binary format.
- `Calculator.Client`: Generated stub, implement `Calculator.Iface`.

### 5.3 Thực hiện RPC Calls (`perform()`)

```java
private static void perform(Calculator.Client client) throws TException {
    // 1. ping()
    client.ping();                          // send_ping() + recv_ping()
    
    // 2. add(1, 1)
    int sum = client.add(1, 1);             // send_add() + recv_add()
    
    // 3. calculate(1, Work{DIVIDE, 1, 0})
    Work work = new Work();
    work.op = Operation.DIVIDE;
    work.num1 = 1;
    work.num2 = 0;
    try {
        int quotient = client.calculate(1, work);
    } catch (InvalidOperation io) {
        System.out.println("Invalid operation: " + io.why);
    }
    
    // 4. calculate(1, Work{SUBTRACT, 15, 10})
    work.op = Operation.SUBTRACT;
    work.num1 = 15;
    work.num2 = 10;
    int diff = client.calculate(1, work);   // returns 5
    
    // 5. getStruct(1) - từ SharedService
    SharedStruct log = client.getStruct(1); // returns entry với value="5"
}
```

### 5.4 Client Stub Chi Tiết

Ví dụ với `add()`:

```java
// Calculator.Client (generated)
public int add(int num1, int num2) throws TException {
    send_add(num1, num2);      // Ghi request ra output protocol
    return recv_add();         // Đọc response từ input protocol
}

public void send_add(int num1, int num2) throws TException {
    add_args args = new add_args();
    args.setNum1(num1);
    args.setNum2(num2);
    sendBase("add", args);     // Ghi message header + args struct
}

public int recv_add() throws TException {
    add_result result = new add_result();
    receiveBase(result, "add"); // Đọc message header + result struct
    if (result.isSetSuccess()) {
        return result.success;
    }
    throw new TApplicationException(MISSING_RESULT, "add failed");
}
```

**Serialization flow của `sendBase`**:
```
writeMessageBegin("add", CALL, seqid)
  writeStructBegin("add_args")
    writeFieldBegin("num1", I32, 1)
      writeI32(num1)
    writeFieldEnd()
    writeFieldBegin("num2", I32, 2)
      writeI32(num2)
    writeFieldEnd()
    writeFieldStop()
  writeStructEnd()
writeMessageEnd()
oprot.getTransport().flush()
```

---

## 6. Data Flow Chi Tiết: Từ Object đến Wire

### 6.1 Struct Serialization (`Work.write()`)

```java
public void write(TProtocol oprot) throws TException {
    oprot.writeStructBegin(STRUCT_DESC);        // "Work"
    
    oprot.writeFieldBegin(NUM1_FIELD_DESC);     // field 1, I32
    oprot.writeI32(this.num1);
    oprot.writeFieldEnd();
    
    oprot.writeFieldBegin(NUM2_FIELD_DESC);     // field 2, I32
    oprot.writeI32(this.num2);
    oprot.writeFieldEnd();
    
    if (this.op != null) {
        oprot.writeFieldBegin(OP_FIELD_DESC);   // field 3, ENUM (I32)
        oprot.writeI32(this.op.getValue());
        oprot.writeFieldEnd();
    }
    
    if (this.comment != null && isSetComment()) {
        oprot.writeFieldBegin(COMMENT_FIELD_DESC); // field 4, STRING
        oprot.writeString(this.comment);
        oprot.writeFieldEnd();
    }
    
    oprot.writeFieldStop();                     // Dấu hiệu kết thúc struct
    oprot.writeStructEnd();
}
```

### 6.2 Struct Deserialization (`Work.read()`)

```java
public void read(TProtocol iprot) throws TException {
    iprot.readStructBegin();
    while (true) {
        TField field = iprot.readFieldBegin();
        if (field.type == TType.STOP) break;    // Gặp fieldStop thì dừng
        
        switch (field.id) {
            case 1: // NUM1
                if (field.type == TType.I32) {
                    struct.num1 = iprot.readI32();
                    struct.setNum1IsSet(true);
                } else {
                    TProtocolUtil.skip(iprot, field.type);
                }
                break;
            case 2: // NUM2
                // ...
            default:
                TProtocolUtil.skip(iprot, field.type); // Skip unknown field
        }
        iprot.readFieldEnd();
    }
    iprot.readStructEnd();
}
```

---

## 7. SSL/TLS Flow

### 7.1 Server Side

```java
TSSLTransportParameters params = new TSSLTransportParameters();
params.setKeyStore("src/main/resources/.keystore", "thrift", null, null);
TServerTransport serverTransport = TSSLTransportFactory.getServerSocket(9091, 0, null, params);
```

- Server load **keystore** (`/.keystore`) chứa private key + certificate.
- `TSSLTransportFactory` tạo `SSLServerSocket` thay vì plain `ServerSocket`.

### 7.2 Client Side

```java
TSSLTransportFactory.TSSLTransportParameters params = 
    new TSSLTransportFactory.TSSLTransportParameters();
params.setTrustStore("src/main/resources/.truststore", "thrift", "SunX509", "JKS");
TTransport transport = TSSLTransportFactory.getClientSocket("localhost", 9091, 0, params);
```

- Client load **truststore** (`/.truststore`) chứa trusted certificates.
- Handshake SSL xảy ra khi kết nối được thiết lập.
- Sau handshake, `TBinaryProtocol` hoạt động trên `SSLSocket` như bình thường.

---

## 8. Versioning trong Project

Project này minh họa versioning qua `include "shared.thrift"`:

- `shared.thrift` định nghĩa `SharedService` và `SharedStruct`.
- `tutorial.thrift` extends `shared.SharedService`, thêm các method mới (`ping`, `add`, `calculate`, `zip`).
- Điều này cho phép:
  - Nhiều service chia sẻ cùng một struct/interface.
  - Thay đổi `Calculator` mà không ảnh hưởng `SharedService`.

---

## 9. Tóm tắt Luồng Thực Thi

```
┌─────────────┐
│ JavaClient  │
│  perform()  │
└──────┬──────┘
       │ 1. TSocket.open()
       ▼
┌─────────────┐
│  Transport  │ (TCP Socket / SSL Socket)
└──────┬──────┘
       │ 2. TBinaryProtocol
       ▼
┌─────────────┐
│    Wire     │ (Serialized bytes: message + struct)
└──────┬──────┘
       │ 3. TServerSocket.accept()
       ▼
┌─────────────┐
│  Transport  │ (Server side)
└──────┬──────┘
       │ 4. TBinaryProtocol
       ▼
┌─────────────┐
│  Processor  │ (Calculator.Processor)
│  process()  │
└──────┬──────┘
       │ 5. HashMap lookup by method name
       ▼
┌─────────────┐
│   Handler   │ (CalculatorHandler)
│ business logic│
└──────┬──────┘
       │ 6. Return value / Exception
       ▼
┌─────────────┐
│  Processor  │ (Serialize result)
└──────┬──────┘
       │ 7. Response qua Transport
       ▼
┌─────────────┐
│ JavaClient  │ (recv_* parse result)
└─────────────┘
```

---

## 10. Điểm Quan Trọng

1. **Generated code không được sửa**: `build/generated-sources/thrift/` được tạo bởi `thrift` compiler. Business logic chỉ nằm trong `CalculatorHandler.java`.
2. **Handler là Singleton**: Trong ví dụ này, một `CalculatorHandler` instance được dùng cho cả simple và secure server, chia sẻ cùng một `HashMap` log.
3. **Protocol/Transport agnostic**: `CalculatorHandler` không biết client dùng plain TCP hay SSL, cũng không biết protocol là Binary hay Compact.
4. **Field Identifiers**: Mọi field trong `Work`, mọi argument trong service methods đều có số thứ tự (1, 2, 3, 4...). Đây là cơ chế versioning cốt lõi của Thrift.

---

*Flow documentation based on actual source code in this workspace.*
