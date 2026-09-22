# Kafka Streaming — Partition-Key Ordering & Retention

## The Problem
For a tax-compliance-style pipeline, business events need to be processed
**in order per business**, while different businesses are processed **in
parallel** — and the system needs to guarantee no duplicate/lost data during
reconciliation.

## The Mechanism — Partition Keys
Every event is published keyed by `businessId`:
```java
kafkaTemplate.send("tax-compliance-events", businessId, event);
```
Kafka's default partitioner hashes the key to deterministically choose a
partition. Same key → same partition, every time → strict per-business
ordering, while different businesses spread across partitions in parallel.

**Direct equivalent to ActiveMQ's `JMSXGroupID`** (see [Module 2](../02-activemq-partitioning)) — same goal, different mechanism: Kafka uses partition
assignment; JMS uses sticky consumer routing.

## Batch Consumption
```java
factory.setBatchListener(true);
```
Configured via `KafkaConsumerConfig`, with `MAX_POLL_RECORDS_CONFIG` capping
batch size. The listener receives `List<ConsumerRecord<String,String>>`
instead of one record at a time — reduces offset-commit overhead and enables
bulk downstream operations (e.g. one DB batch-insert instead of N individual
inserts), which is usually the real bottleneck at scale, not Kafka
consumption itself.

## Retention (Kafka's TTL — and why it's different from a queue's)

Unlike ActiveMQ/JMS, where a message is typically removed once consumed,
**Kafka retains messages for a configured period regardless of consumption
status.** A message can be consumed instantly and still sit in the log for
the full retention window, available for replay by a new consumer group or
reprocessing after a bug fix.

**Two retention levers, settable per-topic:**
```java
.config("retention.ms", "86400000")       // time-based: 1 day (demo value)
.config("retention.bytes", "104857600")   // size-based: 100MB per partition
```
Whichever limit is hit first triggers cleanup of the oldest segments.

**Kafka defaults to `retention.ms = 604800000` (7 days)** if not overridden.

**Special case — compacted topics:**
```properties
cleanup.policy = compact
```
Instead of time/size-based deletion, keeps only the *latest value per key* —
useful for "current state" topics (e.g. latest balance per account) rather
than a full event history.

**Important distinction for interviews:** a JMS message TTL usually means
"discard if not consumed within X time" — a delivery-guarantee concept tied
to consumption. Kafka retention is "how long do we keep this in the log at
all, for replay" — completely decoupled from whether it's been consumed.
Don't conflate the two if asked to compare them.

**Production implication for a compliance pipeline:** Kafka's retention
should generally be shorter than your actual audit/compliance retention
requirement — Kafka is a durable buffer/replay log, not a system of record.
The permanent audit trail belongs in a data lake or equivalent long-term
store, with Kafka retention tuned just long enough to support replay/
reprocessing scenarios (e.g. 7-30 days), not years.

## Hands-on proof (from actually running this)
Publishing rapid, back-to-back events for the same `businessId` produced:
```
=== Received batch of 2 records ===
  Event: event-2 | Key (businessId): business-123 | Partition: 0
  Event: event-3 | Key (businessId): business-123 | Partition: 0
=== Received batch of 2 records ===
  Event: event-4 | Key (businessId): business-123 | Partition: 0
  Event: event-5 | Key (businessId): business-123 | Partition: 0
```
Confirms three things simultaneously: batching works, strict ordering is
preserved across batches, and partition assignment is deterministic per key
— all 4 events, across 2 separate batch deliveries, landed on partition 0.

## Run it
```bash
# Start the broker
docker compose up -d

# Run the app (from your IDE or):
mvn spring-boot:run

# Publish events (PowerShell)
irm -Uri "http://localhost:8080/publish/business-123?event=invoice-created" -Method POST
irm -Uri "http://localhost:8080/publish/business-456?event=payment-received" -Method POST
irm -Uri "http://localhost:8080/publish/business-123?event=invoice-paid" -Method POST
```
Watch the app console for `=== Received batch of N records ===` output.
