# Redis Distributed Lock — Duplicate Record Prevention

## The Real Problem
In a distributed, multi-server platform, external partners can send both a
**CREATE** and a **MODIFY** event for the same entity (e.g., a reservation)
near-simultaneously. Because requests can land on different servers or
different threads, both can pass an "does this already exist?" check before
either one writes — producing **two internal records for what should be one
entity**, each with the same external ID but a different internal ID.

This is a classic **check-then-act race condition**, and a single JVM's
`synchronized` keyword can't fix it, because the two racing requests aren't
necessarily even in the same JVM.

## The Fix
A **distributed lock**, built on Redis via Redisson, scoped to the entity's
external ID. Wrapped in a reusable `withLock()` utility so calling code never
manages lock/unlock lifecycle directly — reducing the chance of a leaked lock
from a missed `finally` block.

Key design decisions:
- **Redis over DynamoDB**: Redis was already the platform's caching backbone —
  lower cost/complexity than introducing a second distributed data store.
- **Lock scoped tightly**: only wraps the actual check-and-write, not upstream
  validation/computation — minimizes contention time and throughput impact.
- **Lease time (auto-expiry)**: protects against a permanently stuck lock if
  the holding process crashes mid-task.
- **`isHeldByCurrentThread()` check before unlock**: never release a lock this
  thread doesn't actually own — important after lease expiry, where another
  thread may have legitimately re-acquired it.

## Proof
`ReservationServiceRaceConditionTest` demonstrates both:
1. **The bug, reproduced** — 10 concurrent requests for the same external ID
   against the unsafe implementation produce multiple distinct internal IDs.
2. **The fix, verified** — the same concurrent load against the lock-wrapped
   implementation produces exactly one internal ID, every time.

Uses **Testcontainers** to run against a real Redis instance, not a mock —
so the test proves the fix against actual Redis lock semantics.

## Run it
```bash
# Requires Docker running locally (for Testcontainers' Redis instance)
mvn test
```

Watch the console output for `[UNSAFE]` vs `[SAFE]` distinct-ID counts —
the unsafe run will typically show more than 1; the safe run will always show
exactly 1.

## Production notes (from real-world usage)
- Deployed against **Redis Cluster with replicas** — a node failure triggers
  failover rather than losing all lock state outright. There's a small
  theoretical window during replica promotion where a lock could be lost
  (a known Redlock edge case), mitigated by short lock TTLs plus a downstream
  message-queue retry safety net.
- In production, this pattern has run since 2019–2020 with no observed
  incidents.
