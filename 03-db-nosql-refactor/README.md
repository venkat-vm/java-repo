# DB Over-Fetching Refactor — Lightweight Projections

## The Real Problem
Many read calls — existence checks, status lookups — only needed 1-2
columns. But Hibernate's default entity loading pulled the **entire object
graph**: deeply nested and in places circular associations, all eagerly
loaded regardless of what the caller actually needed. This drove unnecessary
DB load and connection pressure across the platform.

## The Fix — Lightweight Projections
Instead of always materializing the full entity, define a narrow, read-only
**projection** scoped to exactly what a given use case needs. Spring Data JPA
generates a SQL query selecting *only* those columns — no joins to
associated tables at all.

**Production note:** the real system used Hibernate `.hbm.xml` mapping files
to define these lite entities, since the codebase's persistence layer
already used XML-based mappings elsewhere — a deliberate choice to work
within the existing pattern rather than migrate the whole persistence layer
to annotations, which would have been a much larger, riskier effort. This
demo uses Spring Data's interface-based projections (`ReservationSummary`)
instead, which is the modern, annotation-based equivalent of the same
underlying idea.

## What's in this demo
- `Reservation` — the full entity, with **deliberately EAGER** associations
  to `Partner` (which itself eagerly loads `OwnedProperty`) and
  `RateHistoryEntry` — reproducing the real multi-level, expensive object
  graph.
- `ReservationSummary` — the lightweight projection interface: just `id`,
  `externalId`, `status`.
- `ReservationRepository` — exposes both the expensive full-fetch method and
  the cheap projection method, side by side, for direct comparison.

## Proof
`OverFetchingComparisonTest` uses **Hibernate's own statistics API**
(`Statistics.getQueryExecutionCount()`) to measure — not guess — the actual
number of SQL queries triggered by each approach:
- **Full entity fetch**: 1 query for the reservation, plus additional joins/
  queries for `Partner`, `OwnedProperty`, and `RateHistoryEntry`.
- **Projection fetch**: exactly 1 query, selecting only the 3 needed columns.

## Run it
```bash
mvn test
```
No external database required — uses an in-memory H2 database, with
Hibernate statistics enabled via `application.properties`.

## Production impact (from real-world usage)
This fix, combined with query-execution-time profiling, targeted indexing,
and moving reads to dedicated replicas, was part of a broader stability
initiative that took database CPU from **90-98% at peak down to an average
of 35-50%**, and reduced total DB connections by roughly **30%**.
