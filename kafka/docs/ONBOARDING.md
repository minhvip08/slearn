# Apache Kafka — Developer Onboarding Guide

> Generated from the project knowledge graph at commit `230e2c2f`.  
> Last analyzed: 2026-05-05

---

## 1. Project Overview

**Apache Kafka** is an open-source distributed event streaming platform used by thousands of companies for high-performance data pipelines, streaming analytics, data integration, and mission-critical applications.

| | |
|---|---|
| **Languages** | Java (primary), Scala, Groovy (build scripts) |
| **Build system** | Gradle (multi-module) |
| **Frameworks** | Gradle, JUnit, Log4j2 |
| **Current version** | 4.4.0-SNAPSHOT |
| **Min Java** | 11 (clients/streams), 17 (everything else) |

### Quick start

```bash
# Build
./gradlew jar

# Start a broker (KRaft mode, no ZooKeeper)
bin/kafka-server-start.sh config/server.properties

# Create a topic
bin/kafka-topics.sh --create --topic my-topic --bootstrap-server localhost:9092

# Produce / consume
bin/kafka-console-producer.sh --topic my-topic --bootstrap-server localhost:9092
bin/kafka-console-consumer.sh --topic my-topic --from-beginning --bootstrap-server localhost:9092

# Run tests
./gradlew test
```

---

## 2. Repository Layout

```
kafka/
├── clients/          # Producer, Consumer, Admin client APIs (Java)
├── core/             # Broker logic (Scala) — KafkaApis, ReplicaManager, SocketServer
├── server/           # Broker services (Java) — BrokerLifecycleManager, FetchManager
├── server-common/    # Shared broker utilities (Java)
├── metadata/         # KRaft controller + metadata images (Java)
├── raft/             # Raft consensus protocol (Java)
├── storage/          # Log storage + tiered/remote storage (Java)
├── streams/          # Kafka Streams library (Java + Scala bindings)
├── connect/          # Kafka Connect framework (Java)
├── group-coordinator/# Consumer group & share group coordination (Java)
├── share-coordinator/# Share group coordinator
├── transaction-coordinator/ # Transaction coordinator
├── coordinator-common/      # Shared coordinator utilities
├── generator/        # Protocol/schema code generator
├── tools/            # CLI tools (kafka-topics, kafka-configs, etc.)
├── shell/            # kafka-metadata-shell for KRaft log inspection
├── config/           # Runtime configuration templates
├── bin/              # Shell launcher scripts
├── docker/           # Docker image build/test/release tooling
├── .github/workflows/# CI/CD GitHub Actions
├── gradle/           # Centralized dependency versions
├── checkstyle/       # Code style rules
└── build.gradle      # Root build script
```

---

## 3. Architecture Layers

Kafka's codebase is organized into 11 architectural layers:

### Layer 1 — Client Libraries

**What it is:** The public-facing producer, consumer, and admin client APIs that application developers use directly.

| Key File | Role |
|---|---|
| `clients/.../producer/Producer.java` | Producer interface |
| `clients/.../producer/KafkaProducer.java` | Producer implementation — async sends, batching, transactions |
| `clients/.../consumer/Consumer.java` | Consumer interface |
| `clients/.../consumer/KafkaConsumer.java` | Consumer implementation — group coordination, poll loop |
| `clients/.../admin/Admin.java` | Admin interface — topics, ACLs, configs |
| `clients/.../admin/KafkaAdminClient.java` | Admin implementation |
| `clients/.../NetworkClient.java` | Shared network layer underlying all clients |

---

### Layer 2 — Protocol & Common

**What it is:** Shared protocol definitions, serialization, security, and common data types used across all Kafka components.

| Key File | Role |
|---|---|
| `clients/.../common/protocol/ApiKeys.java` | Every Kafka protocol API key with version ranges |
| `clients/.../common/serialization/Serializer.java` | Serializer interface (bytes out) |
| `clients/.../common/serialization/Deserializer.java` | Deserializer interface (bytes in) |
| `clients/.../common/requests/DescribeTopicPartitionsRequest.java` | Example protocol request class |
| `clients/.../common/security/ssl/CommonNameLoggingTrustManagerFactoryWrapper.java` | SSL debugging utility |

---

### Layer 3 — Broker Core

**What it is:** The heart of the Kafka broker — request handling, partition management, replication, and network I/O. Primarily Scala.

| Key File | Role |
|---|---|
| `core/.../kafka/Kafka.scala` | **Main entry point** — starts `KafkaRaftServer` |
| `core/.../kafka/server/KafkaApis.scala` | Central request dispatcher — every API request lands here |
| `core/.../kafka/server/ReplicaManager.scala` | Manages all partition replicas — produce/fetch, ISR, leader changes |
| `core/.../kafka/cluster/Partition.scala` | A single partition's state machine |
| `core/.../kafka/network/SocketServer.scala` | NIO network layer — acceptors, processors, request queues |
| `core/.../kafka/server/BrokerServer.scala` | Broker lifecycle (KRaft mode) — wires everything together |
| `core/.../kafka/server/KafkaRaftServer.scala` | Top-level KRaft server — combines broker + controller |
| `core/.../kafka/server/KafkaConfig.scala` | All broker configuration properties |
| `server/.../BrokerLifecycleManager.java` | Broker state machine — registration, heartbeats, shutdown |
| `server/.../FetchManager.java` | Fetch session management for consumers |
| `server/.../ClientMetricsManager.java` | Client telemetry subscriptions |

---

### Layer 4 — KRaft Consensus

**What it is:** Kafka's built-in Raft implementation that replaced ZooKeeper for metadata consensus (KIP-500).

| Key File | Role |
|---|---|
| `raft/.../raft/RaftClient.java` | Core Raft interface |
| `raft/.../raft/KafkaRaftClient.java` | Raft implementation — leader election, log replication |
| `raft/.../raft/QuorumState.java` | State machine: Unattached → Candidate → Leader/Follower |
| `raft/.../raft/LeaderState.java` | Leader-specific state — follower tracking, high watermark |
| `raft/.../raft/FollowerState.java` | Follower state — current leader, fetch timeouts |
| `raft/.../raft/RaftLog.java` | Raft log abstraction |
| `core/.../kafka/raft/KafkaRaftManager.scala` | Broker-side Raft lifecycle manager |

---

### Layer 5 — Metadata Management

**What it is:** The KRaft controller manages all cluster metadata (topics, partitions, brokers, configs) as a replicated log.

| Key File | Role |
|---|---|
| `metadata/.../controller/QuorumController.java` | **The active controller** — handles all cluster changes |
| `metadata/.../controller/ReplicationControlManager.java` | Topic/partition CRUD, ISR, leadership |
| `metadata/.../controller/ClusterControlManager.java` | Broker registrations and fencing |
| `metadata/.../controller/BrokerHeartbeatManager.java` | Tracks broker liveness in the controller |
| `metadata/.../image/MetadataImage.java` | Immutable snapshot of full cluster state |
| `metadata/.../image/TopicsImage.java` | Topic portion of MetadataImage |
| `metadata/.../metadata/KRaftMetadataCache.java` | Broker-side metadata cache (read path) |
| `core/.../kafka/server/ControllerServer.scala` | Controller lifecycle on a KRaft controller node |

---

### Layer 6 — Storage Engine

**What it is:** Kafka's append-only log storage, organized as rolling segments on disk.

| Key File | Role |
|---|---|
| `storage/.../internals/log/LogSegment.java` | A single on-disk log segment — append, read, index |
| `storage/.../internals/log/LogConfig.java` | Topic-level storage config (retention, segment size, compression) |
| `core/.../kafka/log/LogManager.scala` | Manages all topic partition logs on a broker |
| `storage/api/.../remote/storage/RemoteStorageManager.java` | Plugin interface for tiered storage backends |
| `storage/api/.../remote/storage/RemoteLogMetadataManager.java` | Interface for remote log segment metadata |
| `storage/.../storage/TopicBasedRemoteLogMetadataManager.java` | Default remote metadata impl (Kafka-topic backed) |

---

### Layer 7 — Kafka Streams

**What it is:** A client library for building stream processing applications on top of Kafka. No separate cluster needed — it runs inside your application.

| Key File | Role |
|---|---|
| `streams/.../KafkaStreams.java` | **Entry point** — start/stop the streams application |
| `streams/.../StreamsBuilder.java` | High-level DSL for building topologies |
| `streams/.../Topology.java` | The processing graph (sources → processors → sinks) |
| `streams/.../kstream/KStream.java` | Unbounded record stream with DSL ops (filter, map, join…) |
| `streams/.../kstream/KTable.java` | Materialized changelog / table with DSL ops |
| `streams/.../state/KeyValueStore.java` | State store interface |
| `streams/.../processor/api/Processor.java` | Low-level Processor API interface |
| `streams/.../processor/internals/StreamThread.java` | Processing thread — poll loop + task execution |
| `streams/.../processor/internals/TaskManager.java` | Task assignment and lifecycle |
| `streams/.../StreamsConfig.java` | All Streams configuration |
| `streams/streams-scala/...StreamsBuilder.scala` | Idiomatic Scala DSL wrapper |

---

### Layer 8 — Kafka Connect

**What it is:** A framework for streaming data between Kafka and external systems (databases, object stores, search engines, etc.) using pluggable connectors.

| Key File | Role |
|---|---|
| `connect/api/.../connector/Connector.java` | Abstract connector base — generates task configs |
| `connect/api/.../source/SourceConnector.java` | Source connector — pulls data from external → Kafka |
| `connect/api/.../sink/SinkConnector.java` | Sink connector — pushes data from Kafka → external |
| `connect/api/.../source/SourceTask.java` | Source task — does the actual polling |
| `connect/api/.../sink/SinkTask.java` | Sink task — does the actual writing |
| `connect/api/.../connector/ConnectRecord.java` | Base record class for all Connect data |
| `connect/runtime/.../Worker.java` | Runs connectors and tasks on a single node |
| `connect/runtime/.../distributed/DistributedHerder.java` | Coordinates tasks across a Connect cluster |
| `connect/runtime/.../ConnectMetrics.java` | Metrics infrastructure |
| `connect/runtime/.../ExactlyOnceWorkerSourceTask.java` | EOS source task using Kafka transactions |

---

### Layer 9 — Coordinators

**What it is:** Server-side implementations of group and transaction coordination protocols.

| Key File | Role |
|---|---|
| `group-coordinator/.../GroupCoordinatorService.java` | Routes group operations to partition-level coordinators |
| `group-coordinator/.../GroupMetadataManager.java` | Core state machine — joins, rebalances, partition assignment |

---

### Layer 10 — Configuration

**What it is:** Operational configuration templates shipped with Kafka.

| File | Purpose |
|---|---|
| `config/server.properties` | Broker + controller (combined mode) |
| `config/broker.properties` | Broker-only mode |
| `config/controller.properties` | Controller-only mode |
| `config/producer.properties` | Producer client |
| `config/consumer.properties` | Consumer client |
| `config/connect-distributed.properties` | Connect distributed mode |
| `config/log4j2.yaml` | Broker logging (Log4j2) |

---

### Layer 11 — Build & Infrastructure

**What it is:** Gradle build system, CI/CD pipelines, Docker tooling, and shell scripts.

| File | Purpose |
|---|---|
| `build.gradle` | Root build — plugins, compilation, test config (4209 lines) |
| `settings.gradle` | Lists all subprojects |
| `gradle/dependencies.gradle` | Centralized dependency version management |
| `gradle.properties` | Project version: `4.4.0-SNAPSHOT` |
| `bin/kafka-server-start.sh` | Start the broker |
| `bin/kafka-run-class.sh` | Core JVM launcher for all Kafka tools |
| `.github/workflows/ci.yml` | CI entrypoint (push/PR to trunk) |
| `.github/workflows/build.yml` | Reusable build/test workflow (multi-JDK) |
| `docker/docker_build_test.py` | Build and test Docker images |

---

## 4. Key Concepts

### Event Streaming Model

- **Topics** — append-only, partitioned, replicated logs
- **Partitions** — unit of parallelism and ordering; one leader + N followers
- **Offsets** — position within a partition log; consumers track their own position
- **Consumer Groups** — multiple consumers sharing a topic's partitions

### KRaft (No ZooKeeper)

Kafka 4.x uses **KRaft** (Kafka Raft Metadata mode) exclusively. The controller is now built-in:
- A Raft quorum of controller nodes elects a **QuorumController** leader
- All cluster metadata (topics, partitions, configs, ACLs) is stored in a replicated **metadata log**
- Brokers subscribe to metadata updates via a pull-based replication mechanism
- `KafkaRaftClient` → `QuorumController` → `ReplicationControlManager` is the critical path for cluster changes

### Replication Protocol

- **ISR (In-Sync Replicas)**: the set of replicas that are fully caught up
- **High Watermark**: the highest offset replicated to all ISR members — safe for consumer reads
- **Leader Epoch**: monotonically increasing counter used to detect stale leaders after failover
- `ReplicaManager` (broker) ↔ `ReplicationControlManager` (controller) coordinate leader elections and ISR changes

### Producer Send Path

```
KafkaProducer.send()
  → Partitioner (determines partition)
  → RecordAccumulator (batches records)
  → Sender thread (NetworkClient)
  → Broker SocketServer
  → KafkaApis.handleProduceRequest()
  → ReplicaManager.appendRecords()
  → LogSegment.append()
```

### Consumer Poll Path

```
KafkaConsumer.poll()
  → ConsumerCoordinator (group management, rebalance)
  → FetchRequestManager → NetworkClient
  → Broker KafkaApis.handleFetchRequest()
  → ReplicaManager.fetchMessages()
  → LogSegment.read()
```

### Configuration System

- `AbstractConfig` + `ConfigDef` — type-safe config with validation, documentation, and defaults
- Each subsystem has its own `*Config` class (`ProducerConfig`, `ConsumerConfig`, `StreamsConfig`, etc.)
- Dynamic config changes go through `DynamicBrokerConfig` → `ConfigHandler` → `QuorumController` on the broker

---

## 5. Guided Tour

Follow this sequence to build a mental model of the codebase from the outside in:

### Step 1 — Project Overview

Start with the README and entry point to understand Kafka as a distributed event streaming platform.

- `README.md` — Project description, build instructions, quickstart
- `bin/kafka-server-start.sh` — How a broker is launched
- `bin/kafka-run-class.sh` — The JVM launcher behind all bin scripts
- `core/src/main/scala/kafka/Kafka.scala` — Java `main()` that boots the server

### Step 2 — Build System

Kafka uses Gradle with a 30+ module structure. Understanding the build is essential for navigating the codebase.

- `build.gradle` — Root build: Java/Scala compilation settings, checkstyle, test configuration
- `settings.gradle` — All subprojects listed
- `gradle.properties` — Version: `4.4.0-SNAPSHOT`, Scala: `2.13`
- `gradle/dependencies.gradle` — Every third-party library version in one place

### Step 3 — Client Libraries: Producer

The producer is the simplest complete path through Kafka.

- `clients/.../producer/Producer.java` — The interface (what callers see)
- `clients/.../producer/KafkaProducer.java` — The implementation (async, batching, transactions)
- `clients/.../producer/ProducerConfig.java` — All `acks`, `batch.size`, `linger.ms` settings
- `clients/.../producer/ProducerRecord.java` — The data envelope

### Step 4 — Client Libraries: Consumer & Admin

The consumer adds group coordination complexity; Admin shows the management plane.

- `clients/.../consumer/Consumer.java` — Consumer interface
- `clients/.../consumer/KafkaConsumer.java` — Poll loop, group coordination, offset management
- `clients/.../admin/Admin.java` — Admin interface (topics, ACLs, configs)
- `clients/.../admin/KafkaAdminClient.java` — Admin implementation
- `clients/.../NetworkClient.java` — The shared NIO network layer

### Step 5 — Broker Server Architecture

The broker is where messages actually land.

- `core/.../kafka/server/BrokerServer.scala` — KRaft broker lifecycle — wires all components together
- `core/.../kafka/server/KafkaApis.scala` — Every API request is dispatched here
- `core/.../kafka/server/ReplicaManager.scala` — Partition replicas, ISR, produce/fetch
- `core/.../kafka/network/SocketServer.scala` — NIO acceptors and processors
- `core/.../kafka/cluster/Partition.scala` — A partition's leader/follower state machine

### Step 6 — KRaft Consensus Layer

KRaft replaced ZooKeeper. Understanding it is key to understanding Kafka 4.x.

- `raft/.../raft/KafkaRaftClient.java` — The Raft protocol engine
- `raft/.../raft/RaftClient.java` — The interface
- `raft/.../raft/QuorumState.java` — State machine: Candidate → Leader or Follower
- `raft/.../raft/LeaderState.java` — Leader-specific state
- `raft/.../raft/FollowerState.java` — Follower state

### Step 7 — Metadata Management & Controller

The controller is the source of truth for all cluster state.

- `metadata/.../controller/QuorumController.java` — The active controller
- `metadata/.../controller/ReplicationControlManager.java` — Topics, partitions, ISR
- `metadata/.../controller/ClusterControlManager.java` — Broker registrations
- `metadata/.../image/MetadataImage.java` — Immutable cluster state snapshot
- `core/.../kafka/server/ControllerServer.scala` — Controller node lifecycle

### Step 8 — Storage Engine

Messages are stored as append-only segments on disk.

- `storage/.../internals/log/LogSegment.java` — On-disk segment I/O
- `storage/.../internals/log/LogConfig.java` — Topic-level storage settings
- `core/.../kafka/log/LogManager.scala` — All partition logs on a broker
- `storage/api/.../remote/storage/RemoteStorageManager.java` — Tiered storage plugin API
- `storage/.../TopicBasedRemoteLogMetadataManager.java` — Tiered storage metadata (Kafka-topic backed)

### Step 9 — Kafka Streams

A stream processing library that runs inside your JVM process.

- `streams/.../KafkaStreams.java` — Start/stop a streams application
- `streams/.../StreamsBuilder.java` — DSL for building topologies
- `streams/.../kstream/KStream.java` — Record stream with filter/map/join
- `streams/.../processor/internals/StreamThread.java` — The processing loop
- `streams/.../processor/internals/TaskManager.java` — Task assignment and lifecycle

### Step 10 — Kafka Connect

A framework for integrating Kafka with external systems.

- `connect/api/.../connector/Connector.java` — Abstract connector base
- `connect/api/.../source/SourceTask.java` — Polls data from external → Kafka
- `connect/api/.../sink/SinkTask.java` — Writes data from Kafka → external
- `connect/runtime/.../Worker.java` — Runs connectors/tasks on one node
- `connect/runtime/.../distributed/DistributedHerder.java` — Distributed coordination

### Step 11 — Infrastructure

CI, Docker, and release tooling.

- `.github/workflows/ci.yml` — CI entrypoint on push/PR
- `.github/workflows/build.yml` — Reusable build/test (runs on JDK 17 + 25)
- `docker/docker_build_test.py` — Build and test Docker images
- `docker/docker_release.py` — Push images to registries

---

## 6. Complexity Hotspots

These files have the highest complexity in the codebase. Understand the surrounding context before making changes.

### Critical Path (touch with care)

| File | Why it's complex |
|---|---|
| `core/.../kafka/server/KafkaApis.scala` | Central request dispatcher — any change affects all API paths |
| `core/.../kafka/server/ReplicaManager.scala` | All partition replicas — produce/fetch paths, ISR management |
| `core/.../kafka/cluster/Partition.scala` | Partition leader/follower state machine with concurrency |
| `metadata/.../controller/QuorumController.java` | All cluster mutations pass through here |
| `metadata/.../controller/ReplicationControlManager.java` | Topic/partition state — ISR changes, leader elections |
| `raft/.../raft/KafkaRaftClient.java` | Raft consensus engine — correctness is critical |
| `raft/.../raft/QuorumState.java` | State transitions in Raft — subtle invariants |

### Client-Side Complexity

| File | Why it's complex |
|---|---|
| `clients/.../producer/KafkaProducer.java` | Async batching, transactions, retry logic |
| `clients/.../consumer/KafkaConsumer.java` | Group coordination, offset management, rebalance handling |
| `clients/.../admin/KafkaAdminClient.java` | All admin ops via async request/response |
| `clients/.../NetworkClient.java` | NIO connection management, in-flight requests |

### Streams Complexity

| File | Why it's complex |
|---|---|
| `streams/.../StreamsBuilder.java` | DSL topology construction with optimization passes |
| `streams/.../KafkaStreams.java` | Application lifecycle with state transitions |
| `streams/.../processor/internals/StreamThread.java` | Processing loop + rebalance handling |
| `streams/.../processor/internals/TaskManager.java` | Task assignment across threads |

### Connect Complexity

| File | Why it's complex |
|---|---|
| `connect/runtime/.../Worker.java` | Connector/task lifecycle, producer/consumer management |
| `connect/runtime/.../DistributedHerder.java` | Distributed coordination via group protocol |
| `connect/runtime/.../ExactlyOnceWorkerSourceTask.java` | EOS source tasks using Kafka transactions |

### Infrastructure Complexity

| File | Why it's complex |
|---|---|
| `build.gradle` | 4200+ lines — multi-module Gradle with Scala, Java, test config |
| `bin/kafka-run-class.sh` | JVM classpath and option setup for all tools |
| `core/.../kafka/server/BrokerServer.scala` | Wires all broker components — startup/shutdown ordering matters |
| `core/.../kafka/server/DynamicBrokerConfig.scala` | Live config changes without restart |

---

## 7. Development Workflow

### Building a single module

```bash
./gradlew :clients:jar
./gradlew :core:jar
./gradlew :streams:jar
```

### Running specific tests

```bash
# All tests in a module
./gradlew :clients:test

# A single test class
./gradlew :clients:test --tests RequestResponseTest

# A single test method
./gradlew :core:test --tests kafka.api.ProducerFailureHandlingTest.testCannotSendToInternalTopic
```

### Checking code style

```bash
./gradlew checkstyleMain   # Java checkstyle
./gradlew scalafmtCheck    # Scala formatting
```

### Key Gradle tasks

| Task | Purpose |
|---|---|
| `./gradlew jar` | Build all JARs |
| `./gradlew test` | Run all tests |
| `./gradlew unitTest` | Unit tests only |
| `./gradlew integrationTest` | Integration tests only |
| `./gradlew javadoc` | Generate Javadoc |
| `./gradlew aggregatedJavadoc` | Aggregated Javadoc (all modules) |
| `./gradlew rat` | Apache license header check |

---

## 8. Where to Start

**Working on the client library** (producer/consumer/admin):
→ `clients/src/main/java/org/apache/kafka/clients/`

**Fixing a broker bug** (produce/fetch/replication):
→ `core/src/main/scala/kafka/server/KafkaApis.scala` — find the handler, follow to `ReplicaManager`

**Working on KRaft / controller**:
→ `metadata/src/main/java/org/apache/kafka/controller/QuorumController.java`

**Adding a Raft feature**:
→ `raft/src/main/java/org/apache/kafka/raft/KafkaRaftClient.java`

**Working on Kafka Streams**:
→ `streams/src/main/java/org/apache/kafka/streams/`

**Writing a connector**:
→ `connect/api/src/main/java/org/apache/kafka/connect/`

**Changing configuration**:
→ Find the `*Config.java` or `*Configs.java` in the relevant module, update `ConfigDef`

---

*This guide was auto-generated from the project knowledge graph. For the interactive architecture explorer, run `/understand-dashboard`.*
