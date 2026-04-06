# ADR-001: Offline-First Architecture with PouchDB, CouchDB, ETL, and PostgreSQL

## Status

**Proposed** → (Move to *Accepted* after team review)

---

## Context

The system must support:

* Full **offline-first capability**
* Seamless **data synchronization** when connectivity is restored
* Ability to handle **concurrent updates and conflicts**
* Centralized **data integrity and reporting**
* Flexible **UI-driven conflict resolution**

### Constraints

* Users may work offline for extended periods
* Backend must ensure **strong consistency for final data**
* Data model includes both **document-style input** and **relational reporting**
* System must be **scalable and auditable**

---

## Decision

Adopt a **multi-layer offline-first architecture**:

### Client Layer

* Use **PouchDB**
* Store and mutate data locally
* Sync asynchronously with CouchDB

### Sync Layer

* Use **Apache CouchDB**
* Enable multi-master replication
* Maintain revision history (`_rev`)
* Detect and store conflicts

### Processing Layer (ETL)

* Use **Spring Boot**
* Consume CouchDB `_changes` feed
* Transform JSON documents into relational format
* Apply validation, enrichment, and deduplication
* Ensure idempotent processing

### Source of Truth

* Use **PostgreSQL**
* Maintain final, consistent, and queryable data
* Support reporting and transactional guarantees (ACID)

---

## Architecture Flow

```
[PouchDB Client]
    ↓ (sync)
[CouchDB]
    ↓ (_changes feed)
[Spring Boot ETL]
    ↓
[PostgreSQL (Source of Truth)]
```

---

## Key Design Principles

1. **Offline-First**

    * All user actions are local-first
    * Sync is asynchronous and resilient

2. **Eventual Consistency**

    * Intermediate states may diverge
    * PostgreSQL represents final consistent state

3. **Conflict Visibility**

    * Conflicts are surfaced to UI
    * Users participate in resolution when needed

4. **Idempotent ETL**

    * Each change processed exactly-once (logically)
    * Safe retries supported

5. **Separation of Concerns**

    * Sync handled by CouchDB
    * Business logic handled in ETL
    * Querying handled in PostgreSQL

---

## Consequences

### Positive

* True offline capability with excellent UX
* Decoupled architecture improves flexibility
* PostgreSQL ensures strong data integrity
* Conflict resolution is explicit and controllable
* ETL layer allows advanced transformations

---

### Negative

* Increased system complexity (multiple data stores)
* Eventual consistency may confuse users
* Conflict management requires careful UX design
* ETL pipeline must handle:

    * Ordering
    * Deduplication
    * Failure recovery
* Debugging across layers is difficult

---

## Risks & Mitigations

### Risk: Conflict Explosion

* **Mitigation:**

    * Auto-merge non-overlapping fields
    * Use timestamps or version vectors
    * Limit editable fields where possible

---

### Risk: Duplicate or Out-of-Order Processing

* **Mitigation:**

    * Use `event_id` and `revision`
    * Track last processed sequence (`_seq`)
    * Ensure idempotent writes in ETL

---

### Risk: Data Drift Between Models

* **Mitigation:**

    * Define strict mapping contracts
    * Version document schemas
    * Add validation in ETL layer

---

### Risk: Debugging Complexity

* **Mitigation:**

    * Introduce correlation IDs
    * Centralized logging across layers
    * Build replay capability from `_changes`

---

## Alternatives Considered

### 1. Direct Client → PostgreSQL (REST API)

* ❌ No offline support
* ❌ Poor UX in low connectivity

---

### 2. GraphQL + Offline Cache

* ❌ Limited conflict resolution
* ❌ Complex cache invalidation

---

### 3. CRDT-Based Sync

* ✅ Automatic conflict resolution
* ❌ High complexity
* ❌ Hard to map to relational model

---

### 4. Kafka-Based Streaming Pipeline

* Example: CouchDB → Kafka → ETL → PostgreSQL
* ✅ Better scalability and replay
* ❌ Additional infrastructure overhead

---

## Implementation Notes

### ETL Design

* Consume `_changes` feed continuously
* Store checkpoint (`last_seq`)
* Use upsert strategy in PostgreSQL
* Ensure idempotency via unique keys

---

### Conflict Resolution Strategy

| Type                    | Strategy              |
| ----------------------- | --------------------- |
| Non-overlapping updates | Auto-merge            |
| Same field updated      | Last-write-wins or UI |
| Critical data           | Manual resolution     |

---

### Observability

* Log every stage:

    * Sync
    * ETL processing
    * DB writes
* Add tracing IDs per document
* Build admin dashboard for:

    * Conflicts
    * Sync status
    * ETL lag

---

## Decision Outcome

This architecture is:

* ✅ **Approved for offline-first systems**
* ⚠️ **Requires strong engineering discipline**
* ❌ **Not suitable for strict real-time consistency systems**

---

## Next Steps

* Build ETL prototype (CouchDB `_changes` consumer)
* Define document → relational mapping schema
* Design conflict resolution UI
* Add observability and replay tooling

---
