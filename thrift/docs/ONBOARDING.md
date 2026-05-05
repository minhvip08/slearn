# Apache Thrift — Developer Onboarding Guide

> Auto-generated from the project's knowledge graph on 2026-05-05.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture Overview](#architecture-overview)
3. [The Four-Layer Runtime Stack](#the-four-layer-runtime-stack)
4. [Guided Tour: Learning Path](#guided-tour-learning-path)
5. [Architecture Layers & Key Files](#architecture-layers--key-files)
6. [Key Concepts](#key-concepts)
7. [Complexity Hotspots](#complexity-hotspots)
8. [Build System](#build-system)
9. [Testing](#testing)
10. [Contributing](#contributing)

---

## Project Overview

**Apache Thrift** is a lightweight, language-independent software stack for point-to-point RPC implementation. Thrift provides clean abstractions for data transport, data serialization, and application-level processing. Its code generation system takes a simple Interface Definition Language (IDL) as input and produces interoperable RPC clients and servers across **28+ programming languages**.

| Attribute | Value |
|-----------|-------|
| Language | C++ (compiler), 28+ target languages (libraries) |
| License | Apache 2.0 |
| Build Systems | Autotools, CMake, Cargo, Maven/Gradle, Go Modules |
| CI/CD | GitHub Actions, AppVeyor, Travis CI |

**Supported languages:** autotools, bison, c, cmake, cpp, csharp, d-lang, dart, delphi, erlang, flex, go, haxe, java, javascript, json, kotlin, lua, ocaml, perl, php, python, ruby, rust, smalltalk, swift, thrift, typescript, xml, yaml

---

## Architecture Overview

Thrift has two main components:

```
┌─────────────────────────────────────────────────────┐
│                  Thrift IDL Compiler                │
│   (compiler/cpp/) — Reads .thrift → generates code  │
└──────────────────────────┬──────────────────────────┘
                           │ generates
                           ▼
┌─────────────────────────────────────────────────────┐
│          Per-Language Runtime Libraries             │
│                    (lib/<lang>/)                    │
│  ┌─────────────────────────────────────────────┐   │
│  │  Server Layer    — Manages connections        │   │
│  │  Processor Layer — Dispatches RPC calls      │   │
│  │  Protocol Layer  — Serializes data types     │   │
│  │  Transport Layer — Moves bytes on the wire   │   │
│  └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

The compiler and the runtime libraries are intentionally separate: you run the compiler once to generate type-safe code from your `.thrift` files, then link against the runtime library in your language of choice.

---

## The Four-Layer Runtime Stack

Every language library implements the same four-layer abstraction. From bottom to top:

### 1. Transport Layer
- Moves raw bytes between endpoints
- Abstraction: `open`, `close`, `read`, `write`, `flush`
- Implementations: TCP sockets (`TSocket`), HTTP, memory buffers (`TMemoryBuffer`), TLS, framed (`TFramedTransport`), buffered (`TBufferedTransport`)
- Reference: `lib/cpp/src/thrift/transport/TTransport.h`

### 2. Protocol Layer
- Serializes and deserializes typed data over a transport
- Abstraction: `writeI32`, `readString`, `writeStructBegin`, `readMapEnd`, …
- Implementations: Binary (`TBinaryProtocol`), Compact (`TCompactProtocol`), JSON (`TJSONProtocol`), Multiplexed
- Reference: `lib/cpp/src/thrift/protocol/TProtocol.h`

### 3. Processor Layer
- Reads an RPC method name from the protocol, dispatches to the handler, serializes the response
- Generated from IDL: each Thrift service produces a corresponding `XxxProcessor` class
- Reference: `lib/cpp/src/thrift/TProcessor.h`

### 4. Server Layer
- Accepts connections and manages threading/concurrency
- Implementations: `TSimpleServer` (single-threaded), `TThreadPoolServer`, `TThreadedServer`, `TNonblockingServer`
- Reference: `lib/cpp/src/thrift/server/TServer.h`

---

## Guided Tour: Learning Path

Follow these steps in order to build a complete mental model of Thrift.

### Step 1 — Project Overview
Start with the README to understand what Apache Thrift is: a lightweight, language-independent RPC framework with code generation for 28+ languages. The README introduces the four-layer architecture (transport, protocol, processor, server) and the project hierarchy: `compiler/` for the IDL compiler, `lib/` for per-language runtime libraries, and `test/` for validation.

**Read:** `README.md`, `LANGUAGES.md`

---

### Step 2 — The Thrift IDL Language
Thrift's Interface Definition Language (IDL) is the starting point for all code generation. `ThriftTest.thrift` is the canonical test schema defining structs, services, enums, exceptions, and type annotations. Understanding this file reveals the vocabulary Thrift uses to describe RPC interfaces across all languages.

**Read:** `test/ThriftTest.thrift`, `test/AnnotationTest.thrift`

---

### Step 3 — Compiler Entry Point
The Thrift compiler is a C++ program that reads `.thrift` IDL files and produces source code in target languages. `main.cc` orchestrates the pipeline: parse command-line arguments, invoke the flex/bison parser, build an AST, then dispatch to the appropriate code generator based on the `--gen` flag.

**Read:** `compiler/cpp/src/thrift/main.cc`

---

### Step 4 — Abstract Syntax Tree
After parsing, the compiler represents the IDL as a tree of C++ objects rooted at `t_program`. This AST node holds all structs, services, enums, typedefs, and constants defined in a `.thrift` file. The AST is the compiler's internal representation that code generators traverse to emit language-specific output.

**Read:** `compiler/cpp/src/thrift/parse/t_program.h`

---

### Step 5 — Code Generation Framework
`t_generator.h` defines the base class that all language-specific generators inherit from. Each generator (`t_cpp_generator`, `t_java_generator`, `t_py_generator`, etc.) overrides virtual methods to emit structs, services, and serialization code in its target language. This plugin architecture makes adding new language support modular.

**Read:** `compiler/cpp/src/thrift/generate/t_generator.h`

---

### Step 6 — Transport Layer (C++ Reference)
The transport layer is the lowest layer in Thrift's runtime stack — it moves raw bytes between endpoints. `TTransport.h` defines the abstract interface (open, close, read, write), while `TSocket.h` implements TCP socket communication. Every language library implements this same abstraction, enabling interchangeable transports.

**Read:** `lib/cpp/src/thrift/transport/TTransport.h`, `lib/cpp/src/thrift/transport/TSocket.h`

---

### Step 7 — Protocol Layer (C++ Reference)
Built atop transports, the protocol layer defines how data types are serialized to bytes. `TProtocol.h` declares methods for writing/reading each Thrift type. `TBinaryProtocol` implements a straightforward binary encoding. Other protocols (Compact, JSON) trade space or readability for performance.

**Read:** `lib/cpp/src/thrift/protocol/TProtocol.h`, `lib/cpp/src/thrift/protocol/TBinaryProtocol.h`

---

### Step 8 — Processor and Server Layers
`TProcessor.h` defines the interface that dispatches incoming RPC calls to handler methods — it reads a method name from the protocol, finds the corresponding handler, deserializes arguments, invokes the implementation, and serializes the response. `TServer.h` manages connections and threading, tying transport + protocol + processor together.

**Read:** `lib/cpp/src/thrift/TProcessor.h`, `lib/cpp/src/thrift/server/TServer.h`

---

### Step 9 — Go Library Implementation
The Go library implements the same four-layer architecture using Go idioms. `transport.go` defines the `TTransport` interface, `protocol.go` defines `TProtocol`, and `server.go` ties them together. Comparing these with the C++ versions reveals how Thrift's architecture adapts to different language paradigms.

**Read:** `lib/go/thrift/transport.go`, `lib/go/thrift/protocol.go`, `lib/go/thrift/server.go`

---

### Step 10 — Java and Python Libraries
Java and Python further demonstrate the cross-language pattern. Java uses abstract classes and generics for type safety, while Python uses duck typing and simpler class hierarchies. Despite language differences, both implement identical serialization and transport semantics, ensuring any Java client can talk to any Python server.

**Read:** `lib/java/src/main/java/org/apache/thrift/transport/TTransport.java`,
`lib/java/src/main/java/org/apache/thrift/protocol/TProtocol.java`,
`lib/py/src/transport/TTransport.py`, `lib/py/src/protocol/TProtocol.py`

---

### Step 11 — Build System
Thrift uses a dual build system: GNU Autotools (`configure.ac` + `Makefile.am`) for the traditional Unix build, and CMake (`CMakeLists.txt`) as a modern cross-platform alternative. Both handle building a C++ compiler plus optional libraries in 28+ languages, with feature detection for available language toolchains.

**Read:** `configure.ac`, `CMakeLists.txt`

---

### Step 12 — CI and Contributing
The CI pipeline validates changes across all supported languages using Docker containers. The build workflow runs compiler tests, cross-language integration tests, and style checks. `CONTRIBUTING.md` documents the process for submitting changes: JIRA tickets, commit message format, and the requirement to test across affected languages.

**Read:** `.github/workflows/build.yml`, `CONTRIBUTING.md`

---

## Architecture Layers & Key Files

### IDL Compiler (`compiler/cpp/`)
The Thrift IDL compiler is implemented entirely in C++ using Flex (lexer) and Bison (parser). It parses `.thrift` files into an AST, then dispatches to one of 28+ code generators.

| File | Purpose |
|------|---------|
| `compiler/cpp/src/thrift/main.cc` | Compiler entry point: argument parsing, pipeline orchestration |
| `compiler/cpp/src/thrift/parse/t_program.h` | Root AST node holding all definitions |
| `compiler/cpp/src/thrift/generate/t_generator.h` | Base class for all code generators |
| `compiler/cpp/src/thrift/generate/t_java_generator.cc` | Java code generator (5,909 lines) |
| `compiler/cpp/src/thrift/generate/t_cpp_generator.cc` | C++ code generator (5,194 lines) |
| `compiler/cpp/src/thrift/generate/t_go_generator.cc` | Go code generator (4,427 lines) |
| `compiler/cpp/src/thrift/common.cc` | Shared compiler utilities |
| `compiler/cpp/src/thrift/audit/t_audit.cpp` | IDL compatibility auditing tool |

### C++ Library (`lib/cpp/`) — Reference Implementation
| File | Purpose |
|------|---------|
| `lib/cpp/src/thrift/transport/TTransport.h` | Abstract transport interface |
| `lib/cpp/src/thrift/transport/TSocket.h` | TCP socket transport |
| `lib/cpp/src/thrift/transport/THttpClient.h` | HTTP transport (client-side) |
| `lib/cpp/src/thrift/protocol/TProtocol.h` | Abstract protocol interface |
| `lib/cpp/src/thrift/protocol/TBinaryProtocol.h` | Binary serialization protocol |
| `lib/cpp/src/thrift/protocol/TCompactProtocol.h` | Space-efficient compact protocol |
| `lib/cpp/src/thrift/TProcessor.h` | Processor interface (dispatches RPC calls) |
| `lib/cpp/src/thrift/server/TServer.h` | Abstract server interface |
| `lib/cpp/src/thrift/server/TSimpleServer.h` | Single-threaded server |
| `lib/cpp/src/thrift/server/TThreadPoolServer.h` | Thread-pool server |
| `lib/cpp/src/thrift/async/TAsyncBufferProcessor.h` | Async processor interface |

### JVM Libraries (`lib/java/`, `lib/kotlin/`, `lib/javame/`)
| File | Purpose |
|------|---------|
| `lib/java/src/main/java/org/apache/thrift/transport/TTransport.java` | Java transport abstract class |
| `lib/java/src/main/java/org/apache/thrift/protocol/TProtocol.java` | Java protocol abstract class |
| `lib/java/build.gradle` | Gradle build configuration |
| `lib/kotlin/` | Kotlin coroutine-based library |

### Go Library (`lib/go/thrift/`)
| File | Purpose |
|------|---------|
| `lib/go/thrift/transport.go` | Go `TTransport` interface |
| `lib/go/thrift/protocol.go` | Go `TProtocol` interface |
| `lib/go/thrift/server.go` | Go server implementations |
| `lib/go/thrift/socket.go` | TCP socket transport |
| `lib/go/thrift/http_client.go` | HTTP transport |

### Python Library (`lib/py/`)
| File | Purpose |
|------|---------|
| `lib/py/src/transport/TTransport.py` | Python transport base classes |
| `lib/py/src/protocol/TProtocol.py` | Python protocol base classes |
| `lib/py/src/server/TServer.py` | Python server implementations |
| `lib/py/src/ext/` | C extension for accelerated binary protocol |

### .NET Library (`lib/netstd/`)
| File | Purpose |
|------|---------|
| `lib/netstd/Client/` | Client implementations |
| `lib/netstd/Server/` | Server implementations |
| `lib/netstd/Protocol/` | Protocol implementations |
| `lib/netstd/Transport/` | Transport implementations |

### Other Notable Libraries
- **Ruby** (`lib/rb/`): Includes Thin/Mongrel server adapters and C-accelerated binary protocol
- **PHP** (`lib/php/`): Full transport, protocol, and processor implementations with autoloader
- **Erlang** (`lib/erl/`): OTP-based implementation with rebar build
- **Rust** (`lib/rs/`): Cargo-based implementation
- **Swift** (`lib/swift/`): Swift Package Manager support

### Test Infrastructure (`test/`)
| File | Purpose |
|------|---------|
| `test/ThriftTest.thrift` | Canonical cross-language test IDL |
| `test/AnnotationTest.thrift` | Tests IDL annotations |
| `test/crossrunner/` | Python-based cross-language integration test runner |
| `test/keys/` | TLS certificate/key materials for secure transport testing |
| `test/known_failures_Linux.json` | Known failing cross-language test combinations |

---

## Key Concepts

### IDL-First Design
All Thrift services start with a `.thrift` IDL file. The compiler generates type-safe client/server stubs in target languages. **Never write serialization code by hand** — always run the compiler.

```thrift
// example.thrift
struct Person {
  1: required string name,
  2: optional i32 age,
}

service Greeter {
  string greet(1: Person person),
}
```

### Transport/Protocol Separation
Transports and protocols are independently composable. You can use:
- **Binary protocol** over **TCP socket**
- **JSON protocol** over **HTTP**
- **Compact protocol** over **TLS**

This is achieved through decoration (e.g., `TFramedTransport` wraps any transport).

### Cross-Language Compatibility
Any Thrift client in any language can talk to any Thrift server in any language, provided they use the same protocol and the same IDL version. This is Thrift's primary design goal.

### Version Compatibility
Thrift is designed for non-atomic version changes: add new fields with new field IDs; mark old fields `optional`; never re-use field IDs. This allows clients and servers to be upgraded independently.

### Code Generation Plugin Architecture
Each target language is a separate code generator class (`t_<lang>_generator.cc`) inheriting from `t_generator`. Adding a new language means implementing the generator plugin — the parser and AST are shared across all generators.

---

## Complexity Hotspots

These files are the largest and most complex in the project. Approach them after understanding the surrounding architecture.

| File | Lines | Why Complex |
|------|-------|-------------|
| `compiler/cpp/src/thrift/generate/t_java_generator.cc` | 5,909 | Complete Java code generator — handles all IDL constructs for Java output |
| `compiler/cpp/src/thrift/generate/t_cpp_generator.cc` | 5,194 | Complete C++ code generator — the most feature-rich target |
| `compiler/cpp/src/thrift/generate/t_c_glib_generator.cc` | 4,602 | C/GLib code generator — verbose generated C patterns |
| `compiler/cpp/src/thrift/generate/t_go_generator.cc` | 4,427 | Go code generator with Go-specific idioms (goroutines, interfaces) |
| `compiler/cpp/src/thrift/generate/t_delphi_generator.cc` | 4,130 | Delphi/Pascal code generator |
| `compiler/cpp/src/thrift/generate/t_netstd_generator.cc` | 4,015 | .NET Standard code generator with async/await support |
| `compiler/cpp/src/thrift/generate/t_javame_generator.cc` | 3,303 | Java ME (mobile) code generator |
| `compiler/cpp/src/thrift/generate/t_js_generator.cc` | 3,121 | JavaScript code generator (browser + Node.js) |
| `compiler/cpp/src/thrift/generate/t_haxe_generator.cc` | 3,086 | Haxe code generator |
| `compiler/cpp/src/thrift/generate/t_dart_generator.cc` | 2,580 | Dart code generator |

> **Tip:** All these generators follow the same pattern — they implement `generate_program()` which calls `generate_struct()`, `generate_service()`, etc. Once you understand one generator (e.g., `t_go_generator.cc`), the others are structurally similar.

---

## Build System

### Quick Start (CMake)
```bash
mkdir build && cd build
cmake ..
make -j$(nproc)
sudo make install
```

### Quick Start (Autotools)
```bash
./bootstrap.sh
./configure
make -j$(nproc)
sudo make install
```

### Docker (Recommended for CI parity)
```bash
# Build compiler only
docker build -t thrift-compiler build/docker/
docker run --rm -v $(pwd):/thrift/src thrift-compiler

# Run tests for a specific language
cd build/docker && docker-compose run thrift-compiler test-cpp
```

### Building Individual Language Libraries
Most libraries have their own build system:
- **Java**: `cd lib/java && gradle build`
- **Python**: `cd lib/py && python setup.py install`
- **Go**: No explicit build needed; imported via Go modules
- **Rust**: `cd lib/rs && cargo build`
- **.NET**: `cd lib/netstd && dotnet build`

---

## Testing

### Cross-Language Integration Tests
The `test/crossrunner/` directory contains a Python-based test runner that starts server+client pairs across all language combinations:

```bash
cd test
python test.py --server cpp --client java  # C++ server, Java client
python test.py --list-all                  # Show all test combinations
```

### Running Compiler Tests
```bash
cd compiler/cpp
make check
```

### Known Failures
`test/known_failures_Linux.json` tracks combinations that are expected to fail. Check this before investigating new test failures.

---

## Contributing

1. **File a JIRA ticket** at [issues.apache.org/jira/browse/THRIFT](https://issues.apache.org/jira/browse/THRIFT) for non-trivial changes
2. **Branch naming**: use the JIRA ticket ID (e.g., `THRIFT-9999`)
3. **PR title format**: `THRIFT-9999: Short description` (triggers JIRA linkage)
4. **Commit message format**:
   ```
   THRIFT-9999: Short description of the change
   Client: cpp,java,go   (affected languages)
   ```
5. **Tests required**: add cross-language tests for runtime changes; add compiler tests for IDL/generator changes
6. **Style check**: run `make style` before submitting
7. **Single squashed commit** per PR

See [CONTRIBUTING.md](../CONTRIBUTING.md) for full details.

---

*Generated from the knowledge graph at commit `c1710f06e13d8db9729f715a858e68df092ae14f`.*
