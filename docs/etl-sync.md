# ETL sync: CouchDB to PostgreSQL

Technical reference for the bidirectional sync pipeline between CouchDB and PostgreSQL.

## Audience

Backend engineers working on `ofa-api`. Read this before modifying anything in the `features/sync/` package.

## Data flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        FORWARD SYNC (CouchDB → PG)                 │
│                                                                     │
│  CouchDB                                                            │
│  ┌──────────────────┐                                               │
│  │ GET /_changes    │─── @Scheduled (5s) ───▶ Parse JSON           │
│  │ since={lastSeq}  │                         │                     │
│  │ include_docs     │                         ▼                     │
│  │ filter=_selector │                  PropertySyncService          │
│  └──────────────────┘                   .syncDocument(doc)          │
│                                               │                     │
│                                    ┌──────────┼──────────┐          │
│                                    ▼          ▼          ▼          │
│                                 CREATE     UPDATE    SOFT-DEL      │
│                                    │          │          │          │
│                                    └──────────┴──────────┘          │
│                                               │                     │
│                                               ▼                     │
│                                    PostgreSQL properties             │
│                                    + couchdb_sync_checkpoint         │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                      REVERSE SYNC (PG → CouchDB)                   │
│                                                                     │
│  REST API → PropertyService.create/update/delete                   │
│       │                                                             │
│       ▼                                                             │
│  ApplicationEventPublisher → PropertyChangedEvent                   │
│       │                                                             │
│       ▼ (after commit)                                              │
│  ReverseSyncService                                                 │
│       │                                                             │
│       ├── Load Property from PG (with couchdbRev)                   │
│       ├── Build CouchDB JSON document                               │
│       ├── GET current _rev from CouchDB (if needed)                 │
│       └── PUT /{db}/{docId}?rev={rev} → CouchDB                    │
└─────────────────────────────────────────────────────────────────────┘
```

## Sync pipeline

### Step 1: Poll `_changes` feed

`SyncService.pollChanges()` runs every 5 seconds via `@Scheduled(fixedDelayString="${ofa.sync.poll-interval:5000}")`.

```
POST /properties/_changes?since={lastSeq}&include_docs=true&filter=_selector
Content-Type: application/json

{"selector":{"type":"property"}}
```

The `_selector` filter ensures only property documents are returned. `include_docs=true` embeds the full document in each change entry, avoiding N+1 GETs.

### Step 2: Parse response

CouchDB returns:

```json
{
  "last_seq": "123-g1AAAA...",
  "pending": 0,
  "results": [
    {
      "seq": "121-g1AAAA...",
      "id": "property::a1b2c3d4-...",
      "changes": [{"rev": "2-abc123"}],
      "doc": { "_id": "property::a1b2c3d4-...", "_rev": "2-abc123", "type": "property", ... }
    }
  ]
}
```

Each entry in `results` is processed individually.

### Step 3: Transform and upsert

`PropertySyncService.syncDocument(JsonNode doc)`:

1. Extract UUID from `_id`: `doc.get("_id").asText().replace("property::", "")`
2. Parse all fields: name, property_type, status, description, address fields, geolocation, contact, operational, tenant_id, timestamps
3. Look up existing row: `propertyRepository.findById(uuid)`
4. Apply the appropriate action:

| Condition | Action |
|-----------|--------|
| No existing row, `deleted_at` is null | INSERT (create) |
| No existing row, `deleted_at` is set | INSERT with deleted_at + status ARCHIVED (was created and deleted while offline) |
| Existing row, CouchDB `updated_at` >= PG `updated_at` | UPDATE (merge fields) |
| Existing row, CouchDB `updated_at` < PG `updated_at` | SKIP (PG is newer) |
| Any row with `deleted_at` set | Set `deleted_at` + `status = ARCHIVED` |

5. Store `_rev` in `couchdb_rev` column for reverse sync.

### Step 4: Save checkpoint

After processing all changes, update the checkpoint:

```sql
UPDATE couchdb_sync_checkpoint SET last_seq = ?, updated_at = NOW() WHERE id = 'properties_changes';
```

If the application crashes mid-batch, the next poll restarts from the last saved checkpoint. Some documents may be reprocessed — the idempotent upsert logic handles this.

## Bootstrap process

On first run (no checkpoint row exists), `SyncService` performs a full bootstrap:

1. Fetch all existing property documents from CouchDB:
   ```
   GET /properties/_all_docs?include_docs=true&startkey="property::"&endkey="property::\uffff"
   ```
2. Process each document through `PropertySyncService.syncDocument()`.
3. Fetch current `update_seq` from `GET /properties`.
4. Save checkpoint with that `update_seq`.

This ensures all pre-existing CouchDB data is imported before incremental polling begins.

The bootstrap runs inside `@EventListener(ApplicationReadyEvent.class)`, so it executes after the Spring context is fully initialized (including Liquibase migrations and the connection pool).

## Checkpoint management

The `couchdb_sync_checkpoint` table stores one row per tracked database:

| Column | Type | Purpose |
|--------|------|---------|
| `id` | `VARCHAR(100)` PK | e.g., `"properties_changes"` |
| `last_seq` | `VARCHAR(255)` | CouchDB sequence token |
| `updated_at` | `TIMESTAMPTZ` | When the checkpoint was last updated |

Checkpoint is read at the start of each poll cycle and written after all changes in that cycle are processed. This is not a per-document checkpoint — it's a batch-level cursor.

## Configuration reference

Properties in `application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `ofa.sync.enabled` | `true` | Enable/disable the sync scheduler |
| `ofa.sync.couchdb.url` | `http://admin:admin@localhost:5984` | CouchDB base URL with credentials |
| `ofa.sync.couchdb.database` | `properties` | CouchDB database name |
| `ofa.sync.poll-interval` | `5000` | Poll interval in milliseconds |
| `spring.datasource.url` | — | PostgreSQL JDBC URL |
| `spring.datasource.username` | — | PostgreSQL username |
| `spring.datasource.password` | — | PostgreSQL password |

## Error handling

| Error | Handling |
|-------|---------|
| CouchDB unreachable | Log error, skip this poll cycle, retry on next schedule |
| Single document transform failure | Log error with doc ID, continue processing remaining documents |
| Checkpoint save failure | Log error; next poll reprocesses the same batch (idempotent) |
| Reverse sync CouchDB rejection (409 conflict) | Fetch latest `_rev` from CouchDB, retry the PUT once |
| Liquibase migration failure | Application fails to start — requires manual intervention |

## Verification

### Prerequisites
1. Docker Compose running: `docker compose up -d`
2. CouchDB accessible: `curl http://admin:admin@localhost:5984/_up`
3. PostgreSQL accessible: `psql -h localhost -U postgres -d ofa -c "SELECT 1"`

### Forward sync test
1. Start the API: `cd ofa-api && ./mvnw spring-boot:run`
2. Check logs for `SyncService` bootstrap message
3. Verify properties imported:
   ```sql
   SELECT id, name, status, couchdb_rev FROM properties;
   ```
4. In the Next.js frontend, create a new property
5. Wait up to 5 seconds, then verify in PostgreSQL:
   ```sql
   SELECT * FROM properties ORDER BY created_at DESC LIMIT 1;
   ```
6. Edit the property in the frontend → verify `updated_at` changes in PG
7. Delete the property → verify `deleted_at` is set and `status = 'ARCHIVED'`

### Checkpoint test
```sql
SELECT * FROM couchdb_sync_checkpoint;
```
The `last_seq` value should update every poll cycle.

### Reverse sync test
1. Insert a property directly via REST API (or SQL + event)
2. Check CouchDB Fauxton UI at http://localhost:5984/_utils
3. Verify the document appears in the `properties` database with the updated fields
