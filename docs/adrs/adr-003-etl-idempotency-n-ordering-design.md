# ADR-003: ETL Idempotency & Ordering Design

## Status

**Proposed** → (Promote to *Accepted* after load testing & failure simulations)

---

## Context

In the offline-first pipeline:

* **PouchDB** (client writes)
* **Apache CouchDB** (sync + `_changes`)
* **Spring Boot** (ETL processing)
* **PostgreSQL** (Source of Truth)

the ETL layer consumes CouchDB’s `_changes` feed and writes to PostgreSQL.

### Core Problems

1. **Duplicate processing**

    * Retries, restarts, or replays may reprocess the same change

2. **Out-of-order events**

    * Updates may arrive in different order than they were created

3. **At-least-once delivery**

    * `_changes` feed guarantees delivery, not uniqueness

4. **Partial failures**

    * Crash after processing but before checkpoint update

---

## Decision

Adopt a design based on:

> **Idempotent Processing + Monotonic Ordering + Checkpointing**

### Key Principles

* Every event must be **safe to process multiple times**
* Only **latest valid state** should persist
* Processing must be **restart-safe**
* Ordering must be **logically enforced**, not assumed

---

## Architecture Overview

```id="c1t6vz"
[CouchDB _changes]
        ↓
[ETL Consumer]
        ↓
[Idempotency Guard]
        ↓
[Ordering Check]
        ↓
[Transform + Validate]
        ↓
[PostgreSQL Upsert]
        ↓
[Checkpoint Update]
```

---

## Event Model

Each change from CouchDB is normalized into:

```json id="q4cv8k"
{
  "event_id": "docId::rev",
  "doc_id": "user-123",
  "rev": "3-abc",
  "seq": "1001-g1AAA...",
  "updated_at": 1710000000,
  "payload": {...}
}
```

### Key Fields

| Field        | Purpose                             |
| ------------ | ----------------------------------- |
| `event_id`   | Unique identifier (idempotency key) |
| `doc_id`     | Entity identity                     |
| `rev`        | CouchDB revision                    |
| `seq`        | Ordering checkpoint                 |
| `updated_at` | Logical ordering fallback           |

---

## Idempotency Strategy

### 1. Event Deduplication Table

#### Table: `etl_event_log`

| Column        | Description      |
| ------------- | ---------------- |
| event_id (PK) | Unique event     |
| doc_id        | Entity           |
| processed_at  | Timestamp        |
| status        | SUCCESS / FAILED |

---

### 2. Processing Rule

Before processing:

```sql id="o4u1ph"
SELECT 1 FROM etl_event_log WHERE event_id = ?
```

* If exists → **SKIP (already processed)**
* If not → process and insert

---

### 3. Insert After Success

```sql id="x0j7xg"
INSERT INTO etl_event_log(event_id, doc_id, status)
VALUES (?, ?, 'SUCCESS')
ON CONFLICT DO NOTHING;
```

---

### ✅ Result

* Safe retries
* No duplicate writes
* Exactly-once **effect** (not delivery)

---

## Ordering Strategy

### Problem

CouchDB does NOT guarantee strict per-document ordering across distributed clients.

---

### Solution: **Per-Document Version Control**

#### Add version column in PostgreSQL:

```sql id="l6b3pz"
ALTER TABLE users ADD COLUMN version BIGINT;
```

---

### Versioning Options

| Strategy              | Source                 |
| --------------------- | ---------------------- |
| `_rev` numeric prefix | CouchDB                |
| `updated_at`          | Client timestamp       |
| Custom `version`      | Application-controlled |

---

### Recommended: Hybrid Version

```text id="h0l7ap"
version = (rev_number, updated_at)
```

---

### Upsert Rule

```sql id="r8t6sn"
UPDATE users
SET name = ?, phone = ?, version = ?
WHERE id = ?
  AND version < ?;
```

* If incoming version is **older** → ignore
* If newer → update

---

### ✅ Result

* Prevents stale updates
* Ensures monotonic state

---

## Checkpointing Strategy

### Store Last Processed `_seq`

#### Table: `etl_checkpoint`

| Column   | Description             |
| -------- | ----------------------- |
| id       | Singleton               |
| last_seq | Last processed sequence |

---

### Flow

1. Read `last_seq`
2. Fetch `_changes?since=last_seq`
3. Process batch
4. Update `last_seq`

---

### Critical Rule

> ✅ **Checkpoint only AFTER successful DB commit**

---

### Failure Scenario Handling

| Scenario                | Behavior                         |
| ----------------------- | -------------------------------- |
| Crash before checkpoint | Reprocess → safe via idempotency |
| Crash after checkpoint  | No data loss                     |
| Partial batch failure   | Retry batch                      |

---

## Transaction Boundary

Each event processed in a **single transaction**:

```text id="g0r2ta"
BEGIN
  → Check idempotency
  → Apply ordering rule
  → Upsert business data
  → Insert event log
COMMIT
```

---

## Concurrency Model

### Option 1: Single Consumer (Simple)

* Guarantees order
* Lower throughput

---

### Option 2: Partition by `doc_id`

* Parallel processing
* Maintain per-entity order

---

### Option 3: Queue-based (Future)

Introduce **Apache Kafka**:

* Partition by `doc_id`
* Replay support
* Backpressure handling

---

## Data Write Strategy

### Upsert Pattern

```sql id="y1r5ps"
INSERT INTO users(id, name, phone, version)
VALUES (?, ?, ?, ?)
ON CONFLICT (id)
DO UPDATE SET
  name = EXCLUDED.name,
  phone = EXCLUDED.phone,
  version = EXCLUDED.version
WHERE users.version < EXCLUDED.version;
```

---

## Handling Deletes

CouchDB uses `_deleted=true`

### Strategy

* Map to soft delete:

```sql id="z4l9df"
UPDATE users
SET deleted = true, version = ?
WHERE id = ?
  AND version < ?;
```

---

## Replay & Recovery

### Full Replay

* Reset checkpoint
* Reprocess all `_changes`

### Partial Replay

* Replay from specific `seq`

### Requirements

* Idempotency must hold
* No side effects outside DB

---

## Observability

Track:

| Metric             | Purpose                   |
| ------------------ | ------------------------- |
| ETL lag (seq diff) | Sync delay                |
| Duplicate rate     | Idempotency effectiveness |
| Out-of-order drops | Ordering issues           |
| Throughput         | Performance               |

---

## Risks & Mitigations

### Risk: Event Log Growth

* **Mitigation:**

    * TTL cleanup (e.g. 7–30 days)
    * Archive old entries

---

### Risk: Clock Skew (timestamps)

* **Mitigation:**

    * Prefer `_rev` ordering
    * Use server-generated time if possible

---

### Risk: Hotspot on Same `doc_id`

* **Mitigation:**

    * Partition processing
    * Batch updates

---

### Risk: Long Transactions

* **Mitigation:**

    * Keep ETL logic minimal
    * Avoid external calls

---

## Decision Outcome

| Aspect      | Result                     |
| ----------- | -------------------------- |
| Idempotency | Strong                     |
| Ordering    | Deterministic per entity   |
| Scalability | Medium → High (with Kafka) |
| Complexity  | Medium                     |

---

## Final Verdict

> This design guarantees **exactly-once effect**,
> **correct ordering**, and **safe replay**,
> which are essential for reliable offline-first ETL pipelines.

---

## Next Steps

* Implement `_changes` consumer in Spring Boot
* Create `etl_event_log` and `etl_checkpoint` tables
* Add version column to all entities
* Load test with:

    * duplicates
    * out-of-order events
    * crash recovery scenarios

---
