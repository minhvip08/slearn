# Apache Thrift - Hướng Dẫn Tổng Quan

> Tài liệu này được tổng hợp từ: source code Thrift trong workspace, [ThriftTutorial](https://github.com/strat0sphere/ThriftTutorial), và whitepaper gốc của Facebook `"Thrift: Scalable Cross-Language Services Implementation"` (thrift-20070401).

---

## 1. Thrift là gì?

**Apache Thrift** là một framework RPC (Remote Procedure Call) và serialization đa ngôn ngữ, được phát triển ban đầu tại Facebook (nay là Meta) và sau đó open-sourced qua Apache Software Foundation.

Mục tiêu chính của Thrift:
- **Giao tiếp liên ngôn ngữ** hiệu quả và đáng tin cậy.
- **Định nghĩa một lần**, sinh code cho nhiều ngôn ngữ (Java, C++, Python, Go, PHP, v.v.).
- Tách biệt rõ ràng giữa **data definition**, **transport**, **protocol**, và **RPC processing**.

---

## 2. Kiến trúc tổng quan

Thrift được thiết kế theo kiến trúc phân lớp với 4 thành phần cốt lõi:

```
+------------------------------------------+
|           Application Layer               |
|    (Handler / Client stub generated)      |
+------------------------------------------+
|           Processor (TProcessor)          |
|    (Điều phối RPC call từ/đến service)   |
+------------------------------------------+
|           Protocol (TProtocol)            |
|    (Encoding/Decoding: Binary, JSON...)  |
+------------------------------------------+
|           Transport (TTransport)          |
|    (TCP, File, Memory, SSL, HTTP...)     |
+------------------------------------------+
```

### 2.1 Transport Layer
- Cung cấp giao tiếp I/O trừu tượng, không phụ thuộc vào protocol hay ngôn ngữ.
- Các implementation phổ biến:
  - `TSocket`: TCP/IP stream socket.
  - `TServerSocket`: Server socket chấp nhận kết nối.
  - `TFramedTransport`: Thêm frame size header, hỗ trợ non-blocking.
  - `TBufferedTransport`: Buffer read/write.
  - `TMemoryBuffer`: Đọc/ghi trực tiếp từ memory.
  - `TSSLTransportFactory`: Hỗ trợ SSL/TLS.
  - `TFileTransport`: Ghi log Thrift struct ra disk và replay lại.

### 2.2 Protocol Layer
- Định nghĩa cách dữ liệu được **encode** trước khi gửi và **decode** khi nhận.
- Interface `TProtocol` hỗ trợ các hàm cơ bản:
  - `writeMessageBegin`, `writeStructBegin`, `writeFieldBegin`, `writeMapBegin`, ...
  - `readMessageBegin`, `readStructBegin`, `readFieldBegin`, `readMapBegin`, ...
- Các protocol implementation:
  - **TBinaryProtocol**: Binary format mặc định, hiệu quả, gọn nhẹ.
  - **TCompactProtocol**: Binary nhỏ gọn hơn, dùng variable-length encoding.
  - **TJSONProtocol**: JSON format, human-readable.
  - **TDebugProtocol**: Text format để debug.

### 2.3 Processor Layer
- `TProcessor` là interface đơn giản duy nhất:
  ```java
  boolean process(TProtocol in, TProtocol out) throws TException
  ```
- Thrift compiler sinh ra `Processor` class (ví dụ: `Calculator.Processor`) có chứa logic:
  - Đọc tên method từ request.
  - Dispatch đến đúng `ProcessFunction` trong một `HashMap`.
  - Gọi method trên `handler` instance do developer cung cấp.
  - Ghi kết quả trả về client.

### 2.4 Server Layer
- `TServer` quản lý vòng đờikết nối, thread pool, và vòng lặp `serve()`.
- Các loại server:
  - `TSimpleServer`: Single-threaded, đơn giản, chỉ dùng cho test.
  - `TThreadPoolServer`: Thread-pool, mỗi request được xử lý bởi một thread trong pool.
  - `TNonblockingServer` (C++): Non-blocking I/O dùng libevent.

---

## 3. Thrift IDL (Interface Definition Language)

Thrift sử dụng file `.thrift` để định nghĩa types và service interfaces. File này là ngôn ngữ trung lập, dùng để generate code cho các target languages.

### 3.1 Base Types

| Thrift Type | Mô tả |
|-------------|-------|
| `bool`      | Boolean, 1 byte |
| `i8` (byte) | Signed 8-bit integer |
| `i16`       | Signed 16-bit integer |
| `i32`       | Signed 32-bit integer |
| `i64`       | Signed 64-bit integer |
| `double`    | 64-bit floating point |
| `string`    | String (UTF-8 hoặc binary) |
| `binary`    | Blob (byte array) |

> **Lưu ý**: Thrift **không hỗ trợ unsigned integers** vì không có kiểu tương đương trong nhiều ngôn ngữ (Python, Java...).

### 3.2 Containers

```thrift
list<type>       // Ordered list, cho phép duplicate (STL vector, Java ArrayList)
set<type>        // Set các phần tử unique (STL set, Java HashSet)
map<type1,type2> // Map key-value (STL map, Java HashMap, Python dict)
```

### 3.3 Structs và Exceptions

```thrift
struct Work {
  1: i32 num1 = 0,
  2: i32 num2,
  3: Operation op,
  4: optional string comment,
}

exception InvalidOperation {
  1: i32 whatOp,
  2: string why
}
```

- Mỗi field có một **field identifier** (số nguyên dương) duy nhất trong struct.
- Có thể khai báo `optional` để field không bắt buộc phải có trong serialized output.
- `exception` tương đương `struct` nhưng được xử lý như exception trong target language.

### 3.4 Enums

```thrift
enum Operation {
  ADD = 1,
  SUBTRACT = 2,
  MULTIPLY = 3,
  DIVIDE = 4
}
```

### 3.5 Typedefs và Constants

```thrift
typedef i32 MyInteger
const i32 INT32CONSTANT = 9853
const map<string,string> MAPCONSTANT = {'hello':'world', 'goodnight':'moon'}
```

### 3.6 Namespaces

```thrift
namespace java tutorial
namespace cpp tutorial
namespace py tutorial
```

### 3.7 Includes

```thrift
include "shared.thrift"
```
- Truy cập qua prefix: `shared.SharedStruct`, `shared.SharedService`.

---

## 4. Services

Service là phần quan trọng nhất trong Thrift - định nghĩa các RPC methods.

```thrift
service Calculator extends shared.SharedService {
   void ping(),
   i32 add(1:i32 num1, 2:i32 num2),
   i32 calculate(1:i32 logid, 2:Work w) throws (1:InvalidOperation ouch),
   oneway void zip()
}
```

- **Return type**: Bất kỳ Thrift type nào, hoặc `void`.
- **Arguments**: Mỗi argument có field identifier, tương tự struct fields.
- **Exceptions**: Có thể khai báo exception có thể throw.
- **`oneway`**: Client gửi request và không đợi response. Bắt buộc phải là `void`.
- **`extends`**: Kế thừa từ service khác.

---

## 5. Code Generation

### 5.1 Các file được sinh ra

Khi chạy `thrift --gen java tutorial.thrift`, compiler sinh ra các class:

| File | Mô tả |
|------|-------|
| `Calculator.java` | Chứa `Iface`, `Client`, `Processor`, `args/result` inner classes cho mỗi method |
| `Work.java` | Struct `Work` với serialization, `read()`, `write()`, `__isset` |
| `Operation.java` | Enum `Operation` implement `TEnum` |
| `InvalidOperation.java` | Exception class |
| `SharedStruct.java`, `SharedService.java` | Từ `shared.thrift` |

### 5.2 Cấu trúc generated Client

```java
public static class Client extends shared.SharedService.Client implements Iface {
    public void ping() throws TException {
        send_ping();
        recv_ping();
    }
    public int add(int num1, int num2) throws TException {
        send_add(num1, num2);
        return recv_add();
    }
}
```

- `send_*`: Tạo `args` struct, ghi ra output protocol.
- `recv_*`: Đọc `result` struct từ input protocol, trả về giá trị hoặc throw exception.

### 5.3 Cấu trúc generated Processor

```java
public static class Processor<I extends Iface> extends TBaseProcessor<I> {
    private static <I extends Iface> Map<String, ProcessFunction<I, ? extends TBase>> getProcessMap(...) {
        Map<String, ProcessFunction<I, ? extends TBase>> processMap = new HashMap<>();
        processMap.put("ping", new ping());
        processMap.put("add", new add());
        // ...
        return processMap;
    }
}
```

- Processor sử dụng `HashMap<String, ProcessFunction>` để lookup method name với độ phức tạp O(1).
- Mỗi `ProcessFunction` implement logic đọc args, gọi handler, và ghi result.

---

## 6. Versioning

Thrift hỗ trợ versioning tốt nhờ **field identifiers**.

### 6.1 Nguyên tắc

- Mỗi field trong struct có một `fieldId` (i16) duy nhất.
- Khi deserialize, nếu gặp `fieldId` không quen biết, generated code **skip** field đó dựa trên type specifier.
- Nếu một field expected không xuất hiện, `__isset` flag sẽ là `false`.

### 6.2 Các trường hợp versioning

1. **Thêm field, old client → new server**: Server thấy field không set → dùng default behavior.
2. **Xóa field, old client → new server**: Server nhận field cũ → ignore.
3. **Thêm field, new client → old server**: Server không nhận diện fieldId → skip.
4. **Xóa field, new client → old server**: ⚠️ Nguy hiểm nhất. Old server không có default cho field bị thiếu. Khuyến nghị: deploy server mới **trước** client mới.

### 6.3 Isset

Mỗi generated struct có một `__isset` structure (hoặc bitfield) để theo dõi field nào đã được set:

```java
private byte __isset_bitfield = 0;
public boolean isSetNum1() {
    return EncodingUtils.testBit(__isset_bitfield, __NUM1_ISSET_ID);
}
```

---

## 7. Cách sử dụng trong Project này

### 7.1 Định nghĩa service

File `thrift/tutorial.thrift` định nghĩa `Calculator` service kế thừa `shared.SharedService`.
File `thrift/multiplication.thrift` định nghĩa `MultiplicationService` đơn giản.

### 7.2 Build và Generate Code

Project sử dụng Gradle với task `compileThrift`:

```bash
./gradlew compileThrift compileJava
```

Nếu không có `thrift` compiler trong PATH, project sẽ skip generation. Bạn có thể commit code generated vào repo hoặc yêu cầu cài đặt `thrift` compiler.

### 7.3 Chạy Server và Client

```bash
# Terminal 1: Chạy server
./gradlew runServer
# Hoặc: java -cp ... com.simi.CalculatorServer

# Terminal 2: Chạy client
./gradlew runClient --args="simple"
# Hoặc cho SSL:
./gradlew runClient --args="secure"
```

Server mở 2 cổng:
- **9090**: Simple TCP socket (`TSimpleServer`).
- **9091**: SSL-secured socket (`TSSLTransportFactory`).

---

## 8. So sánh với các hệ thống tương tự

| Hệ thống | Mô tả | So với Thrift |
|----------|-------|---------------|
| **SOAP** | XML-based, HTTP-oriented | Parsing overhead cao, nặng nề |
| **CORBA** | Comprehensive, cross-language | Overdesigned, heavyweight |
| **COM** | Windows-centric | Không cross-platform |
| **gRPC** | HTTP/2 + Protocol Buffers | Thrift linh hoạt hơn về transports/protocols, không bắt buộc HTTP/2 |
| **Protocol Buffers** | Google's serialization | Chỉ là serialization, không có built-in RPC framework như Thrift |
| **Pillar** | Tiền thân của Thrift tại Facebook | Thiếu versioning và abstraction |

---

## 9. Tài nguyên tham khảo

1. [Apache Thrift Documentation](https://thrift.apache.org/)
2. [Thrift GitHub Tutorial](https://github.com/strat0sphere/ThriftTutorial)
3. Whitepaper: `"Thrift: Scalable Cross-Language Services Implementation"` - Mark Slee, Aditya Agarwal, Marc Kwiatkowski (Facebook, 2007)
4. [Thrift Tutorial ReadTheDocs](https://thrift-tutorial.readthedocs.io/en/latest/)

---

*Document generated from analysis of workspace source code and external references.*
