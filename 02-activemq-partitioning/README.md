# ActiveMQ Partitioning — Sticky Routing & Throughput Tuning

## The Real Problem
Rates, inventory, and restriction updates for the same property were arriving
as high-volume, concurrent writes. This was overwhelming database replica
sync — replicas couldn't keep pace with the write volume, causing
**replication lag**. Separately, naive parallel consumption risked processing
two updates for the same property **out of order**, causing overwrites and
stale data.

The challenge: parallelize aggressively **across** properties, while
guaranteeing strict **in-order** processing **within** a single property.

## The Fix — JMSXGroupID (Message Groups)
Every outgoing message is stamped with `JMSXGroupID = propertyId` on the
producer side. ActiveMQ guarantees that **all messages sharing a group ID are
routed to the same consumer**, for as long as that consumer stays connected —
"sticky routing." Different property IDs can be picked up by different
consumer threads simultaneously.

This gives you both properties at once:
- **Ordering** — one property's events always land on the same thread, in order
- **Parallelism** — many properties get processed concurrently across threads

## Tuning Levers (all implemented in `JmsConfig.java`)

| Lever | Setting used | Why |
|---|---|---|
| **Concurrency** | `5-10` (min-max listener threads) | The single biggest throughput lever — scales active consumer threads with load |
| **Prefetch size** | `50` | Batches message delivery per round-trip instead of one-at-a-time; too high risks one slow consumer hoarding messages |
| **Ack mode** | `DUPS_OK_ACKNOWLEDGE` | Lazy/batched acknowledgment for higher throughput on high-volume, idempotent-safe event types — trades a small duplicate-delivery risk for speed |
| **Connection pooling** | `PooledConnectionFactory`, max 10 connections | Avoids the cost of creating a raw JMS connection per operation |
| **Retries** | `RedeliveryPolicy`: 5 max retries, exponential backoff starting at 1s | Handles transient downstream failures without hammering a struggling dependency |

## Proof
`PropertyUpdatePartitioningTest` publishes interleaved events for 20
properties (10 events each) against a real ActiveMQ broker (via
Testcontainers) and verifies:
1. **Ordering** — every property's events were handled by exactly one
   consumer thread, never split across threads.
2. **Parallelism** — more than one distinct consumer thread was used overall
   across the 20 properties, proving work wasn't accidentally serialized.

## Run it
```bash
# Requires Docker running locally (for Testcontainers' ActiveMQ instance)
mvn test
```

## Production notes (from real-world usage)
- This pattern eliminated replication lag entirely once rolled out — the
  lock/grouping is scoped at the property level specifically because
  "customer" in this domain means **property**, not individual end-user,
  and grouping needed to match the actual unit of write contention.
- The write itself is what's scoped inside the ordering guarantee —
  validation and computation happen beforehand, so contention time on the
  hot path stays minimal even under high concurrency.
