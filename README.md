# OCI Streaming High Availability (HA) Failover Example

Spring Boot demonstration application for a Kafka API producer and consumer using Oracle Cloud Infrastructure (OCI) Streaming.

The project is designed to run from Eclipse, validate behavior through the console, and demonstrate production-oriented event streaming practices: consistent partition keys, idempotent consumption, manual commit after successful processing, retry with DLQ, lag monitoring, cooperative sticky rebalance, batch processing, and automatic failover to a stream in another OCI region.

## Table of Contents

- [Objective](#objective)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Running from Eclipse](#running-from-eclipse)
- [Configuring OCI Streaming](#configuring-oci-streaming)
- [Regional Failover](#regional-failover)
- [Configuration Reference](#configuration-reference)
- [Console Scenarios](#console-scenarios)
- [Implemented Practices](#implemented-practices)
- [Observability](#observability)
- [Project Structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Security](#security)
- [Useful Commands](#useful-commands)

## Objective

This application simulates an order event flow published to a Kafka-compatible stream and consumed in batches.

The producer publishes `OrderEvent` records to the main topic. The consumer processes records in batches, stores idempotency control in H2, acknowledges offsets only after successful processing, and sends permanently failed messages to a DLQ after the configured retry attempts.

When failover is enabled, the application can also react to an active stream failure. It creates or reuses an equivalent stream in a secondary OCI region, persists the new endpoint to a local file, and starts publishing and consuming through the new Kafka bootstrap endpoint.

## Architecture

The diagrams below use icons from the OCI Architecture Diagram Toolkit and represent the visual architecture for this demo. An editable PowerPoint version is also available: [`OCI_Streaming_HA_Architecture.pptx`](OCI_Streaming_HA_Architecture.pptx).

### High-Level Architecture

![HA Architecture - Spring Boot + OCI Streaming](docs/images/architecture-high-level.png)

### Technical Failover Flow

![Technical failover flow](docs/images/architecture-failover-flow.png)

## Prerequisites

- JDK 8 or later.
- Maven 3.8 or later.
- Eclipse with Maven support (`Existing Maven Project`).
- Local Kafka, Docker-based Kafka, or OCI Streaming.
- For OCI Streaming:
  - OCI user with an Auth Token.
  - Stream Pool with a Kafka-compatible endpoint.
  - Streams created for the primary environment, or permission to create/reuse streams during failover.
  - Valid `~/.oci/config` file when failover must create or locate streams in the secondary region.
  - IAM policies that allow the application to use and, when applicable, manage streams in the target compartment.

## Quick Start

### Local Kafka

With Kafka running locally on `localhost:9092`, the defaults are enough:

```bash
mvn spring-boot:run
```

To produce a fixed number of messages without the interactive prompt:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--demo.producer.interactive=false --demo.producer.message-count=20"
```

To let the application create topics in a local Kafka environment:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--demo.topics.create=true"
```

### OCI Streaming Without Failover

Enable the `oci` profile and provide the Kafka bootstrap endpoint from the Stream Pool:

```bash
export SPRING_PROFILES_ACTIVE=oci
export OCI_STREAMING_BOOTSTRAP_SERVERS="<stream-pool-endpoint>:9092"
export OCI_STREAMING_SASL_JAAS_CONFIG='org.apache.kafka.common.security.plain.PlainLoginModule required username="<tenancy-name>/<user-name>/<stream-pool-ocid>" password="<auth-token>";'
export DEMO_TOPIC_ORDERS="orders-demo"
export DEMO_TOPIC_ORDERS_DLQ="orders-demo.DLQ"

mvn spring-boot:run
```

In OCI Streaming, each stream behaves like a Kafka topic. Create the main stream and the DLQ stream beforehand, or enable topic creation only when testing against local Kafka.

## Running from Eclipse

1. Open Eclipse.
2. Select `File > Import > Existing Maven Projects`.
3. Select this repository folder.
4. Wait for Maven to resolve the dependencies.
5. Open `br.com.demo.ocistreaming.OciStreamingDemoApplication`.
6. Run it as `Java Application` or `Spring Boot App`.
7. Configure environment variables in `Run Configurations > Environment`.
8. Configure program arguments in `Run Configurations > Arguments > Program arguments`.

Example `Program arguments`:

```text
--demo.producer.interactive=false --demo.producer.message-count=50 --demo.consumer.batch-size=10
```

When `demo.producer.interactive=true`, the console asks for the desired message count. Enter the number of messages and press Enter; if no value is provided, the default configured count is used.

## Configuring OCI Streaming

### 1. Create Streams

Create two streams in OCI Streaming:

| Stream | Purpose | Example |
| --- | --- | --- |
| Main stream | Receives order events | `orders-demo` |
| DLQ stream | Receives events that fail after retries | `orders-demo.DLQ` |

Choose the number of partitions according to the expected level of parallelism. This project uses `orderId` as the Kafka key, so all events for the same order go to the same partition and preserve ordering per entity.

### 2. Configure Kafka Authentication

The `oci` profile applies these Kafka settings:

```text
security.protocol=SASL_SSL
sasl.mechanism=PLAIN
```

Provide the JAAS configuration through the environment:

```bash
export OCI_STREAMING_SASL_JAAS_CONFIG='org.apache.kafka.common.security.plain.PlainLoginModule required username="<tenancy-name>/<user-name>/<stream-pool-ocid>" password="<auth-token>";'
```

The exact `username` format must follow the Kafka-compatible configuration for your Stream Pool. In OCI environments, it usually includes tenancy, user, and Stream Pool OCID. The `password` is the user's Auth Token, not the OCI Console login password.

### 3. Provide Endpoint and Topics

```bash
export OCI_STREAMING_BOOTSTRAP_SERVERS="<stream-pool-endpoint>:9092"
export DEMO_TOPIC_ORDERS="orders-demo"
export DEMO_TOPIC_ORDERS_DLQ="orders-demo.DLQ"
```

The endpoint usually looks like this:

```text
cell-1.streaming.<region>.oci.oraclecloud.com:9092
```

### 4. Run

```bash
export SPRING_PROFILES_ACTIVE=oci
mvn spring-boot:run
```

## Regional Failover

The regional failover design was inspired by the [`SilvioCristiano/Resilient-HA-OCI-Streaming`](https://github.com/SilvioCristiano/Resilient-HA-OCI-Streaming) example.

When `DEMO_FAILOVER_ENABLED=true`, the application can move from the active stream to a stream in another region when a failure compatible with unavailability or transient throttling is detected. Examples include timeouts, disconnections, HTTP 500, and HTTP 429 responses.

### Failover Flow

1. The producer or health check detects a failure on the active endpoint.
2. `StreamingFailoverCoordinator` coordinates one active switch at a time.
3. `OciStreamProvisioner` uses the OCI SDK and the `~/.oci/config` file.
4. The application creates or reuses the secondary main stream and the secondary DLQ stream.
5. The OCI messages endpoint is converted to a Kafka bootstrap endpoint using the configured port, `9092` by default.
6. `StreamingFailoverStateStore` persists the active state in `./data/stream.properties`.
7. `ActiveStreamingTargetResolver` resolves producer and consumer settings to the secondary endpoint.
8. `KafkaClientSwitchService` restarts the Kafka listeners so they consume from the new stream.
9. The producer retries the message that triggered failover.

### Minimal Failover Configuration

```bash
export SPRING_PROFILES_ACTIVE=oci
export DEMO_FAILOVER_ENABLED=true
export DEMO_FAILOVER_COMPARTMENT_ID="<ocid1.compartment...>"
export DEMO_FAILOVER_TARGET_REGION="sa-vinhedo-1"
export DEMO_OCI_CONFIG_PATH="$HOME/.oci/config"
export DEMO_OCI_PROFILE="DEFAULT"
```

If secondary names are not provided, the application uses the same names as the primary topics:

```bash
export DEMO_FAILOVER_SECONDARY_STREAM_NAME="orders-demo"
export DEMO_FAILOVER_SECONDARY_DLQ_STREAM_NAME="orders-demo.DLQ"
```

If the secondary Stream Pool requires different SASL credentials, configure:

```bash
export DEMO_FAILOVER_SECONDARY_SASL_JAAS_CONFIG='org.apache.kafka.common.security.plain.PlainLoginModule required username="<tenancy-name>/<user-name>/<secondary-stream-pool-ocid>" password="<auth-token>";'
```

You can also provide the secondary bootstrap endpoint directly when you already know it:

```bash
export DEMO_FAILOVER_SECONDARY_BOOTSTRAP_SERVERS="cell-1.streaming.sa-vinhedo-1.oci.oraclecloud.com:9092"
```

### Failover State File

By default, the active state is stored at:

```text
./data/stream.properties
```

Example:

```properties
# Active OCI Streaming failover target
active.role=SECONDARY
active.region=sa-vinhedo-1
active.topic.orders=orders-demo
active.topic.orders-dlq=orders-demo.DLQ
secondary.stream.ocid=ocid1.stream.oc1...
secondary.dlq-stream.ocid=ocid1.stream.oc1...
secondary.messages.endpoint=https://cell-1.streaming.sa-vinhedo-1.oci.oraclecloud.com
secondary.kafka.bootstrap-servers=cell-1.streaming.sa-vinhedo-1.oci.oraclecloud.com:9092
updated.at=2026-07-21T12:00:00Z
```

On the next startup, when `DEMO_FAILOVER_ENABLED=true` and `DEMO_FAILOVER_ACTIVATE_PERSISTED_SECONDARY=true`, the application loads this file and starts publishing and consuming from the saved secondary endpoint.

To force a return to the primary endpoint in a demo, stop the application and remove `./data/stream.properties`, or configure:

```bash
export DEMO_FAILOVER_ACTIVATE_PERSISTED_SECONDARY=false
```

## Configuration Reference

### Kafka and Topics

| Variable | Default | Description |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | empty | Use `oci` to enable SASL_SSL/PLAIN defaults. |
| `OCI_STREAMING_BOOTSTRAP_SERVERS` | `localhost:9092` | Active Kafka bootstrap endpoint. In OCI, use the Stream Pool endpoint with port `9092`. |
| `OCI_STREAMING_SECURITY_PROTOCOL` | `PLAINTEXT` (`SASL_SSL` in the `oci` profile) | Kafka security protocol. |
| `OCI_STREAMING_SASL_MECHANISM` | empty (`PLAIN` in the `oci` profile) | SASL mechanism. |
| `OCI_STREAMING_SASL_JAAS_CONFIG` | empty | JAAS configuration with username and Auth Token. |
| `DEMO_KAFKA_CLIENT_ID` | `spring-boot-oci-streaming-demo` | Client ID used by Kafka clients. |
| `DEMO_CONSUMER_GROUP` | `oci-streaming-demo-consumer` | Consumer group. Change it to reprocess from `earliest`. |
| `DEMO_TOPIC_ORDERS` | `orders-demo` | Main stream/topic. |
| `DEMO_TOPIC_ORDERS_DLQ` | `orders-demo.DLQ` | DLQ stream/topic. |
| `DEMO_TOPICS_CREATE` | `false` | Creates topics through Kafka Admin. Recommended only for local Kafka. |
| `DEMO_TOPICS_PARTITIONS` | `3` | Partitions used when creating topics locally or during failover when `DEMO_FAILOVER_PARTITIONS=0`. |
| `DEMO_TOPICS_REPLICATION_FACTOR` | `1` | Replication factor used when creating local Kafka topics. |

### Producer

| Variable | Default | Description |
| --- | --- | --- |
| `DEMO_PRODUCER_ENABLED` | `true` | Enables or disables the automatic producer. |
| `DEMO_PRODUCER_INTERACTIVE` | `true` | When `true`, asks for the message count in the console. |
| `DEMO_PRODUCER_MESSAGE_COUNT` | `10` | Default message count when there is no interactive input. |
| `DEMO_PRODUCER_DELAY_MS` | `150` | Delay between sends. |
| `DEMO_PRODUCER_START_SEQUENCE` | `1` | Initial sequence used to generate deterministic events. |
| `DEMO_PRODUCER_ORDER_POOL_SIZE` | `5` | Number of different `orderId` values. Controls key distribution. |
| `DEMO_PRODUCER_POISON_EVERY` | `0` | Generates one permanent failure event every N messages. `0` disables it. |
| `DEMO_PRODUCER_TRANSIENT_EVERY` | `0` | Generates one transient failure event every N messages. `0` disables it. |
| `DEMO_PRODUCER_DETERMINISTIC_EVENT_IDS` | `true` | Allows repeated runs with the same `eventId` values to test idempotency. |

### Consumer

| Variable | Default | Description |
| --- | --- | --- |
| `DEMO_CONSUMER_ENABLED` | `true` | Enables or disables the consumer. |
| `DEMO_CONSUMER_CONCURRENCY` | `1` | Number of listener threads. It should not exceed the partition count without a reason. |
| `DEMO_CONSUMER_BATCH_SIZE` | `10` | Maximum batch size per poll. |
| `DEMO_CONSUMER_SIMULATED_WORK_MS` | `100` | Artificial processing time per event. |
| `DEMO_CONSUMER_RETRY_BACKOFF_MS` | `1000` | Delay between error handler attempts. |
| `DEMO_CONSUMER_RETRY_ATTEMPTS` | `3` | Number of attempts before sending to the DLQ. |
| `DEMO_CONSUMER_TRANSIENT_FAILURES_BEFORE_SUCCESS` | `2` | Number of simulated transient failures before success. |
| `DEMO_LAG_MONITOR_ENABLED` | `true` | Enables or disables periodic lag logging. |
| `DEMO_LAG_MONITOR_INTERVAL_MS` | `15000` | Lag monitoring interval. |

### Failover

| Variable | Default | Description |
| --- | --- | --- |
| `DEMO_FAILOVER_ENABLED` | `false` | Enables regional failover. |
| `DEMO_FAILOVER_HEALTH_CHECK_ENABLED` | `true` | Enables periodic health checks for the active endpoint. |
| `DEMO_FAILOVER_ACTIVATE_PERSISTED_SECONDARY` | `true` | On startup, uses the secondary endpoint saved in `stream.properties` when present. |
| `DEMO_FAILOVER_CREATE_STREAMS` | `true` | Creates the secondary stream when it does not exist. If `false`, the stream must already exist. |
| `DEMO_FAILOVER_CREATE_DLQ` | `true` | Creates or reuses the secondary DLQ. |
| `DEMO_FAILOVER_STATE_FILE` | `./data/stream.properties` | File that stores the active endpoint after failover. |
| `DEMO_OCI_CONFIG_PATH` | `$HOME/.oci/config` | OCI configuration file used by the SDK. |
| `DEMO_OCI_PROFILE` | `DEFAULT` | OCI profile used by the SDK. |
| `DEMO_FAILOVER_COMPARTMENT_ID` | empty | Compartment where secondary streams are created or located. Required for failover. |
| `DEMO_FAILOVER_TARGET_REGION` | `sa-vinhedo-1` | Secondary region. |
| `DEMO_FAILOVER_SECONDARY_STREAM_NAME` | empty | Secondary stream name. Empty uses `DEMO_TOPIC_ORDERS`. |
| `DEMO_FAILOVER_SECONDARY_DLQ_STREAM_NAME` | empty | Secondary DLQ name. Empty uses `DEMO_TOPIC_ORDERS_DLQ`. |
| `DEMO_FAILOVER_SECONDARY_BOOTSTRAP_SERVERS` | empty | Manual secondary bootstrap endpoint. If empty, the application derives it from the OCI endpoint. |
| `DEMO_FAILOVER_SECONDARY_SECURITY_PROTOCOL` | empty | Overrides the secondary Kafka security protocol. In the `oci` profile, it inherits `SASL_SSL`. |
| `DEMO_FAILOVER_SECONDARY_SASL_MECHANISM` | empty | Overrides the secondary SASL mechanism. In the `oci` profile, it inherits `PLAIN`. |
| `DEMO_FAILOVER_SECONDARY_SASL_JAAS_CONFIG` | empty | JAAS configuration specific to the secondary Stream Pool. |
| `DEMO_FAILOVER_PARTITIONS` | `0` | Partition count for secondary streams. `0` uses `DEMO_TOPICS_PARTITIONS`. |
| `DEMO_FAILOVER_KAFKA_BOOTSTRAP_PORT` | `9092` | Port used to convert the OCI messages endpoint to a Kafka bootstrap endpoint. |
| `DEMO_FAILOVER_ADMIN_MAX_WAIT_SECONDS` | `120` | Maximum wait time for a stream to become active. |
| `DEMO_FAILOVER_ADMIN_POLL_INTERVAL_SECONDS` | `5` | Poll interval for OCI create/reuse checks. |
| `DEMO_FAILOVER_HEALTH_CHECK_INTERVAL_MS` | `15000` | Health check interval. |
| `DEMO_FAILOVER_HEALTH_CHECK_TIMEOUT_MS` | `5000` | Health check timeout. |

## Console Scenarios

### Send 50 Messages

```text
--demo.producer.interactive=false --demo.producer.message-count=50
```

### Validate Idempotency

Run twice with the same arguments:

```text
--demo.producer.interactive=false --demo.producer.message-count=10 --demo.producer.start-sequence=1
```

Because `demo.producer.use-deterministic-event-ids=true`, the same `eventId` values are generated. On the second run, the consumer detects that the events were already processed and skips duplicates.

### Validate Retry and DLQ

Generate one permanent failure event every 5 messages:

```text
--demo.producer.interactive=false --demo.producer.message-count=15 --demo.producer.poison-every=5 --demo.consumer.retry-attempts=2
```

After the retry attempts are exhausted, those records are published to the DLQ configured in `DEMO_TOPIC_ORDERS_DLQ`.

### Validate Transient Failure

Generate transient events every 3 messages:

```text
--demo.producer.interactive=false --demo.producer.message-count=10 --demo.producer.transient-every=3 --demo.consumer.transient-failures-before-success=2
```

The consumer fails temporarily and then succeeds within the configured retry attempts.

### Validate Batch Processing and Parallelism

```text
--demo.consumer.batch-size=25 --demo.consumer.concurrency=3
```

The parallelism gain depends on the partition count and on the `orderId` key distribution.

### Validate Failover

1. Run with `SPRING_PROFILES_ACTIVE=oci` and `DEMO_FAILOVER_ENABLED=true`.
2. Temporarily interrupt or invalidate the primary endpoint.
3. Wait for the producer or health check to detect the failure.
4. Confirm in the logs that the active role changed to `SECONDARY`.
5. Check `./data/stream.properties`.
6. Restart the application and confirm that it starts from the saved secondary endpoint.

## Implemented Practices

| Practice | Objective | Implementation |
| --- | --- | --- |
| Consistent Partition Key | Preserve ordering per entity and distribute load | `OrderEvent.partitionKey()` uses `orderId` as the Kafka key. |
| Idempotent Consumer | Allow safe reprocessing without duplicates | `JdbcProcessedEventRepository` stores `event_id` as the primary key in `processed_events`. |
| Commit After Success | Avoid message loss | `OrderEventBatchConsumer` calls `Acknowledgment.acknowledge()` only after the batch is processed. |
| Retry + DLQ | Separate transient failures from permanent failures | `KafkaDemoConfig` uses `DefaultErrorHandler`, `FixedBackOff`, and `DeadLetterPublishingRecoverer`. |
| Lag Monitoring | Detect delayed consumption | `ConsumerLagMonitor` reads committed and end offsets through `AdminClient`. |
| Cooperative Sticky Rebalance | Reduce rebalance impact | `partition.assignment.strategy=CooperativeStickyAssignor`. |
| Batch Processing | Increase throughput | Batch listener configured with `ConcurrentKafkaListenerContainerFactory#setBatchListener(true)`. |
| Regional HA Failover | Switch endpoint and stream during a regional failure | The `ha` package creates or reuses the secondary stream, persists state, and restarts consumers. |

## Observability

Lag is logged periodically in the console with group, topic, partition, committed offset, end offset, and total lag.

Enabled Actuator endpoints:

```text
/actuator/health
/actuator/metrics
/actuator/prometheus
```

With Prometheus, scrape:

```text
http://localhost:8080/actuator/prometheus
```

The H2 database is stored at:

```text
./data/processed-events
```

To repeat a demo without the previous idempotency history, stop the application and remove the files under `./data`.

## Project Structure

```text
src/main/java/br/com/demo/ocistreaming
  OciStreamingDemoApplication.java
  config/
    KafkaDemoConfig.java
    KafkaClientPropertiesFactory.java
    StreamingDemoProperties.java
  consumer/
    OrderEventBatchConsumer.java
    OrderEventProcessor.java
    JdbcProcessedEventRepository.java
  domain/
    OrderEvent.java
  ha/
    ActiveStreamingTargetResolver.java
    KafkaClientSwitchService.java
    OciStreamProvisioner.java
    StreamingEndpointState.java
    StreamingFailoverCoordinator.java
    StreamingFailoverHealthMonitor.java
    StreamingFailoverStateStore.java
  monitoring/
    ConsumerLagMonitor.java
  producer/
    DemoOrderEventFactory.java
    DemoProducerRunner.java
    FailoverAwareOrderProducer.java

src/main/resources
  application.yml
  application-oci.yml
  schema.sql
```

## Troubleshooting

| Symptom | Likely Cause | Recommended Action |
| --- | --- | --- |
| `TimeoutException` while producing | Incorrect bootstrap endpoint, unavailable stream, or blocked network | Check `OCI_STREAMING_BOOTSTRAP_SERVERS`, port `9092`, DNS, and network access. |
| `SaslAuthenticationException` | Incorrect JAAS configuration, Auth Token, or Stream Pool OCID | Generate a new Auth Token and review `OCI_STREAMING_SASL_JAAS_CONFIG`. |
| Consumer does not receive messages | Wrong topic, already committed consumer group, or disabled producer | Check `DEMO_TOPIC_ORDERS`, change `DEMO_CONSUMER_GROUP`, or enable the producer. |
| Duplicate messages in the logs | Manual commit can replay records after a failure before commit | This is expected; the `processed_events` table prevents duplicate processing. |
| Events are sent to the DLQ | Simulated permanent failure or insufficient retry attempts | Adjust `DEMO_PRODUCER_POISON_EVERY`, `DEMO_CONSUMER_RETRY_ATTEMPTS`, and inspect the DLQ. |
| Failover does not create a stream | Missing compartment, IAM policy, or OCI config | Check `DEMO_FAILOVER_COMPARTMENT_ID`, `DEMO_OCI_CONFIG_PATH`, `DEMO_OCI_PROFILE`, and permissions. |
| Application starts on the secondary endpoint unexpectedly | Persisted `stream.properties` file | Remove `./data/stream.properties` or use `DEMO_FAILOVER_ACTIVATE_PERSISTED_SECONDARY=false`. |
| Lag always stays at zero | There is no backlog or offsets do not exist yet | Produce more messages, confirm the group id, and check whether the consumer is active. |

## Security

- Do not commit Auth Tokens, sensitive OCIDs, real `~/.oci/config` files, or real `stream.properties` files.
- The `data/` folder is listed in `.gitignore` to avoid publishing the H2 database and failover state.
- Prefer environment variables or a secret manager for SASL/JAAS values.
- Use least-privilege IAM policies. For failover with automatic stream creation, the application needs permission to manage streams in the secondary compartment.

## Useful Commands

Run tests:

```bash
mvn test
```

Run the application:

```bash
mvn spring-boot:run
```

Run with the OCI profile:

```bash
SPRING_PROFILES_ACTIVE=oci mvn spring-boot:run
```

Build the package:

```bash
mvn clean package
```
