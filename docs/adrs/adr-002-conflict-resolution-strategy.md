# ADR-002: Conflict Resolution Strategy (Deep Dive)

## Status

**Proposed** → (Promote to *Accepted* after validation with real data)

---

## Context

In the offline-first architecture using:

* **PouchDB**
* **Apache CouchDB**
* **Spring Boot**
* **PostgreSQL**

conflicts occur when:

* Multiple clients modify the **same document offline**
* Sync produces **multiple `_rev` branches**
* CouchDB stores **conflicting revisions**

The system must:

* Prevent data loss
* Minimize user friction
* Maintain PostgreSQL integrity (Source of Truth)

---

## Decision

Adopt a **Hybrid Conflict Resolution Strategy**:

> **Auto-resolve when safe**
> **Escalate to UI when necessary**
> **Preserve all conflicting states for auditability**

---

## Conflict Types

### 1. Field-Level Non-Overlapping Conflicts

**Example:**

```json
// Rev A
{ "name": "Alice", "phone": "123" }

// Rev B
{ "name": "Alice", "address": "HCM" }
```

✅ **Resolution:** Auto-merge
➡ Result:

```json
{ "name": "Alice", "phone": "123", "address": "HCM" }
```

---

### 2. Same Field Conflicts

**Example:**

```json
// Rev A
{ "phone": "123" }

// Rev B
{ "phone": "999" }
```

⚠️ **Resolution Options:**

* Last-write-wins (timestamp)
* Priority-based (role/device)
* Escalate to UI

---

### 3. Structural Conflicts

**Example:**

* One client deletes a document
* Another updates it

⚠️ **Resolution:**

* Use **soft delete (`deleted = true`)**
* Preserve both states
* Require manual resolution if critical

---

### 4. Semantic Conflicts (Business Logic)

**Example:**

* Quantity = 5 vs Quantity = 10
* Price changed in two places

❗ Cannot be safely auto-merged

➡ **Resolution:** UI + domain rules

---

## Resolution Strategy Matrix

| Conflict Type             | Auto | Rule-Based | UI Required |
| ------------------------- | ---- | ---------- | ----------- |
| Non-overlapping fields    | ✅    | —          | ❌           |
| Same field (non-critical) | ✅    | Timestamp  | ❌           |
| Same field (critical)     | ❌    | Optional   | ✅           |
| Structural                | ❌    | Partial    | ✅           |
| Semantic                  | ❌    | ❌          | ✅           |

---

## Resolution Algorithm (ETL Layer)

Executed in **Spring Boot** during `_changes` processing:

### Step 1: Detect Conflict

* Check `_conflicts` field from CouchDB
* Load all conflicting revisions

---

### Step 2: Normalize Documents

* Flatten JSON structure
* Align schema versions

---

### Step 3: Field-Level Diff

For each field:

* Compare values across revisions
* Categorize:

    * SAME
    * DIFFERENT
    * MISSING

---

### Step 4: Apply Merge Rules

#### Rule Priority:

1. Non-overlapping → merge
2. Same field:

    * If non-critical → timestamp-based
    * If critical → mark conflict
3. Structural → mark conflict

---

### Step 5: Produce Resolution Result

```json
{
  "resolved": {...mergedDoc},
  "conflicts": [
    {
      "field": "price",
      "values": [100, 120],
      "strategy": "manual_required"
    }
  ]
}
```

---

### Step 6: Persist

* If **no conflicts**:
  → Upsert into **PostgreSQL**

* If **conflicts exist**:
  → Store in `conflict_queue` table
  → Notify UI

---

## Data Model for Conflict Tracking

### Table: `conflict_queue`

| Column          | Description              |
| --------------- | ------------------------ |
| id              | Conflict ID              |
| entity_id       | Business entity          |
| doc_id          | CouchDB doc ID           |
| revisions       | JSON of conflicting revs |
| conflict_fields | JSON list                |
| status          | OPEN / RESOLVED          |
| created_at      | Timestamp                |

---

## UI Conflict Resolution Design

### Requirements

* Show **side-by-side diff**
* Highlight conflicting fields
* Allow:

    * Select A / B
    * Manual edit
* Show metadata:

    * Timestamp
    * Device/user

---

### UX Modes

#### 1. Simple Mode

* Radio select per field:

    * “Use Version A”
    * “Use Version B”

#### 2. Advanced Mode

* Editable merged document
* JSON diff view

---

## Conflict Resolution Flow

```id="g9r9je"
[CouchDB Conflict]
        ↓
[ETL Detects]
        ↓
[Auto Merge Attempt]
        ↓
 ┌───────────────┐
 │ No Conflict   │ → Save to PostgreSQL
 └───────────────┘
        ↓
 ┌───────────────┐
 │ Conflict باقی │ → Store + UI
 └───────────────┘
        ↓
[User Resolves]
        ↓
[Reprocess → PostgreSQL]
```

---

## Advanced Strategies

### 1. Field-Level Priority Rules

```yaml
priority:
  phone: latest
  name: trusted_source
  price: manual
```

---

### 2. Vector Clocks (Optional)

* Track causality instead of timestamps
* More accurate but complex

---

### 3. CRDT (Future Option)

* Automatic conflict-free merging
* Not suitable for relational SoT (complex mapping)

---

### 4. Operation-Based Model (Recommended Evolution)

Instead of syncing full documents:

```json
{
  "op": "UPDATE_PHONE",
  "entityId": "user-1",
  "value": "123",
  "ts": 123456
}
```

✅ Benefits:

* Easier merge
* Fewer conflicts
* Audit-friendly

---

## Risks & Mitigations

### Risk: Too Many Manual Conflicts

* **Mitigation:**

    * Increase auto-merge coverage
    * Limit editable fields
    * Introduce locking (optional)

---

### Risk: Incorrect Auto-Merge

* **Mitigation:**

    * Mark critical fields explicitly
    * Keep original revisions for audit

---

### Risk: Poor UX

* **Mitigation:**

    * Keep UI simple
    * Provide clear explanations
    * Show impact preview

---

## Observability

Track:

* Conflict rate (% of docs)
* Auto-resolve success rate
* Avg resolution time
* Top conflicting fields

---

## Decision Outcome

| Aspect       | Result |
| ------------ | ------ |
| Flexibility  | High   |
| Complexity   | High   |
| User Control | High   |
| Automation   | Medium |

---

## Final Verdict

> This hybrid strategy balances **automation and control**,
> enabling safe scaling of offline-first systems while preserving **data integrity**.

---

## Next Steps

* Implement ETL conflict detection module
* Design conflict resolution UI
* Define field-level criticality rules
* Add metrics dashboard

---
