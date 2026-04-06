# ADR-004: ETL implementation strategy

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-04-06 |
| **Supersedes** | ADR-001 (architecture overview), ADR-003 (idempotency & ordering) |
| **Decision makers** | Engineering team |

## Context

ADR-001 defines the offline-first architecture (PouchDB → CouchDB → ETL → PostgreSQL). ADR-002 defines conflict resolution strategy. ADR-003 defines idempotency, ordering, and checkpointing. This ADR makes the concrete implementation decisions for the Spring Boot ETL service.

Three questions not answered by existing ADRs:
1. How to consume the CouchDB `_changes` feed (feed mode)?
2. Which HTTP client to use?
3. How to implement reverse sync (PostgreSQL → CouchDB)?

## Decision

### 1. `feed=normal` with `@Scheduled` polling (5-second interval)

Use Spring's `@Scheduled(fixedDelayString="${ofa.sync.poll-interval:5000}")` to poll `POST /{db}/_changes?since={lastSeq}&include_docs=true&filter=_selector` with `feed=normal`.

**Alternatives considered:**

| Approach | Pros | Cons |
|----------|------|------|
| `feed=normal` + `@Scheduled` | Simple, debuggable, no thread management, easy restart | Up to 5s latency |
| `feed=longpoll` | Near-real-time | Blocks a thread; timeout/reconnect complexity in Spring MVC |
| `feed=continuous` | True real-time | Streaming HTTP parsing; complex error handling |
| WebFlux `WebClient` streaming | Reactive, efficient | Requires `spring-boot-starter-webflux`; reactive paradigm for one feature |

**Why polling:** The PouchDB ↔ CouchDB sync already uses a 10-second heartbeat. 5-second ETL latency is well within acceptable bounds. Zero thread-management complexity.

### 2. RestClient (Spring Framework 7)

Use Spring Framework 7's `RestClient` for all CouchDB HTTP communication.

**Alternatives:**

| Approach | Status | Fit |
|----------|--------|-----|
| `RestClient` | Current recommended | Fluent API, synchronous, built on same HTTP engine as RestTemplate |
| `RestTemplate` | Maintenance mode | Works but not forward-looking |
| `WebClient` (WebFlux) | Active | Reactive; overkill for sync polling |
| Apache HttpClient 5 | Active | No Spring integration; manual lifecycle |

### 3. Event-driven reverse sync via `@TransactionalEventListener(AFTER_COMMIT)`

When the REST API creates, updates, or deletes a property, the service layer publishes a `PropertyChangedEvent`. A `ReverseSyncService` listens and pushes the change to CouchDB via HTTP PUT.

**Alternatives:**

| Approach | Pros | Cons |
|----------|------|------|
| `@TransactionalEventListener(AFTER_COMMIT)` | Simple, no infrastructure, fires only on commit | Synchronous; CouchDB failure is logged, not retried |
| Scheduled PG poll | Decoupled | Needs dirty tracking column; adds latency |
| Outbox pattern | Reliable, retryable | Overkill for POC; requires Kafka/RabbitMQ |
| Debezium CDC | Industry standard | Requires logical replication, Kafka Connect, Kafka |

**Why event-driven:** Zero additional infrastructure. CouchDB write is best-effort — failures are logged and the next forward sync cycle reconciles.

## Consequences

### Positive
- Simplicity: entire pipeline is synchronous, scheduled, debuggable
- Restart safety: checkpoint in PostgreSQL (per ADR-003)
- Incremental upgrade path: can move to `feed=continuous` + WebClient or outbox pattern without redesigning transform logic

### Negative
- 5-second forward sync latency
- Reverse sync is best-effort (no retry on CouchDB failure)
- Single document type filtering (`type: "property"`) — adding entities requires extending filter and transform
- No batch optimization — individual `findById` + `save` per document
