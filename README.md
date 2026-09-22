# Java Engineering Solutions

Working demos of distributed-systems and backend engineering patterns I've
applied in production — each one grounded in a real problem, not a toy
example.

## Modules

| # | Module | Problem it solves |
|---|---|---|
| 01 | [Redis Distributed Lock](./01-redis-distributed-lock) | Preventing duplicate record creation under concurrent, distributed writes |
| 02 | [ActiveMQ Partitioning](./02-activemq-partitioning) | Message-group-based sticky routing for ordered, parallel processing |
| 03 | [DB Over-Fetching Refactor](./03-db-nosql-refactor) | Reducing over-fetching via lightweight entity projections |
| 04 | [Kafka Streaming](./04-kafka-streaming) | Event-driven processing with partition-key ordering |
| 05 | Flink Windowing *(coming soon)* | Stateful stream aggregation with event-time correctness |

Each module includes working code, a test that proves the problem *and* the
fix, and a README explaining the real-world context.
