# Data model

Property entity schema and document conventions for the OFA offline-first PMS.

## Audience

Developers working on the data layer (`src/lib/db/`). You should read this before modifying `schema.ts`, `property-repository.ts`, or `sync.ts`.

## Document structure

Every PouchDB document follows this shape:

```typescript
{
  // System fields
  _id: "property::a1b2c3d4-5678-...",    // Prefix + crypto.randomUUID()
  _rev: "1-abc...",                        // CouchDB revision (auto-managed)
  type: "property",                        // Discriminator for view filtering

  // Core
  name: "Grand Hotel",                     // Required, min 1 character
  property_type: "HOTEL",                  // Enum, see Property types
  status: "DRAFT",                         // Enum, see Status lifecycle
  description: "",                         // Optional

  // Address
  address_line1: "",
  address_line2: "",
  city: "",
  state_province: "",
  postal_code: "",
  country: "",                             // ISO 3166-1 alpha-2

  // Geolocation
  latitude: undefined,                     // number, optional
  longitude: undefined,                    // number, optional

  // Contact
  phone: "",
  email: "",                               // Valid email or empty string
  website: "",                             // Valid URL or empty string

  // Operational
  timezone: "",                            // IANA timezone (e.g. "Asia/Bangkok")
  currency: "",                            // ISO 4217 (e.g. "THB")
  total_rooms: undefined,                  // Non-negative integer, optional

  // Multi-tenancy + audit
  tenant_id: "tenant::demo",               // Required
  created_at: "2024-01-15T10:30:00.000Z",  // ISO 8601, set on create
  updated_at: "2024-01-15T10:30:00.000Z",  // ISO 8601, set on every write
  deleted_at: null,                        // ISO 8601 or null (soft-delete)
}
```

## ID convention

The `_id` field uses the format `{type}::{uuid}`:

- **Prefix** (`property::`): Enables efficient `allDocs` range queries without a Mango index. Provides namespace isolation when the schema grows to include rooms, bookings, and guests.
- **UUID**: Generated with `crypto.randomUUID()`.
- **URLs**: The prefix is stripped in URLs. A property with `_id: "property::abc-123"` is accessed at `/properties/abc-123`. The repository layer reconstructs the prefix.

The `type` discriminator field and the `_id` prefix serve different purposes:

| Mechanism | Purpose | Query type |
|-----------|---------|------------|
| `_id` prefix | Storage partitioning | `allDocs({ startkey: "property::", endkey: "property::\uffff" })` |
| `type` field | Logical filtering | Mango queries, filtered replication |

## Property types

```typescript
const PROPERTY_TYPES = [
  "HOTEL",
  "RESORT",
  "MOTEL",
  "BNB",
  "VACATION_RENTAL",
  "HOSTEL",
  "GUESTHOUSE",
  "APARTMENT_HOTEL",
  "BOUTIQUE_HOTEL",
  "SERVICED_APARTMENT",
  "OTHER",
] as const;
```

## Status lifecycle

```typescript
const PROPERTY_STATUSES = [
  "DRAFT",      // Initial state on creation
  "ACTIVE",     // Operational property
  "SUSPENDED",  // Temporarily inactive
  "CLOSED",     // Permanently closed
  "ARCHIVED",   // Soft-deleted
] as const;
```

Allowed transitions:

```
DRAFT ──────▶ ACTIVE ──────▶ SUSPENDED ──▶ ACTIVE (reactivation)
  │              │                              │
  │              └──────────────┐               │
  │                             ▼               ▼
  └──▶ ARCHIVED ◀──────── CLOSED ◀──────────────┘
```

| From | To |
|------|----|
| DRAFT | ACTIVE, ARCHIVED |
| ACTIVE | SUSPENDED, CLOSED |
| SUSPENDED | ACTIVE, CLOSED |
| CLOSED | ARCHIVED |
| ARCHIVED | *(terminal state)* |

> **Note**: The current POC does not enforce transition constraints in the repository. Any status can be set freely. Constraints will be added when the backend validates them.

## Soft delete

Documents are never hard-deleted with `_deleted: true`. Instead:

1. Set `deleted_at` to the current ISO timestamp.
2. Set `status` to `ARCHIVED`.
3. All queries filter out documents where `deleted_at` is set.

This preserves the document for audit trails and allows potential recovery.

## Validation

Validation uses Zod schemas defined in `schema.ts`. These schemas are the single source of truth — TypeScript types are inferred with `z.infer`, never handwritten.

| Schema | Purpose |
|--------|---------|
| `PropertyDocSchema` | Full document shape (includes `_id`, `_rev`, system fields) |
| `CreatePropertyInputSchema` | Form input for creation (excludes system fields) |
| `UpdatePropertyInputSchema` | Partial form input for updates (all fields optional) |

Required fields for creation:

- `name` (min 1 character)
- `tenant_id` (min 1 character)

All other fields are optional with sensible defaults (empty strings, `undefined`).

## Repository API

| Method | Returns | Description |
|--------|---------|-------------|
| `create(input, tenantId)` | `Result<PropertyDoc, CreateError>` | Validates input with Zod, generates `_id`, defaults status to DRAFT |
| `getAll(tenantId?)` | `Result<PropertyDoc[], ReadError>` | Fetches all non-deleted properties, sorted by `updated_at` descending |
| `getById(id)` | `Result<PropertyDoc, ReadError>` | Accepts both `property::uuid` and bare `uuid` |
| `getByStatus(status, tenantId?)` | `Result<PropertyDoc[], ReadError>` | Filters `getAll` result by status |
| `update(id, changes)` | `Result<PropertyDoc, UpdateError>` | Read-merge-write with conflict detection |
| `softDelete(id)` | `Result<PropertyDoc, DeleteError>` | Sets `deleted_at` + status ARCHIVED |

All methods return a `Result<T, E>` discriminated union — they never throw. Callers check `result.success`:

```typescript
const result = await propertyRepository.create(input, tenantId);
if (result.success) {
  console.log(result.data._id);  // PropertyDoc
} else {
  console.error(result.error.kind);  // "VALIDATION" | "DB_ERROR"
}
```

## Mango query index

Created on first data access by `ensureIndex()`:

```typescript
db.createIndex({
  index: {
    fields: ["type", "status", "tenant_id", "updated_at", "deleted_at"],
    name: "property-main-index",
  },
});
```

This index supports filtered queries by type, status, and tenant. The `getAll` method uses `allDocs` with prefix range instead of this index — it's faster for full-collection scans. The Mango index is available for future filtered queries.

## Conflict resolution

When sync produces conflicting revisions:

1. `resolveConflicts(docId)` fetches all revisions of the document.
2. Picks the revision with the latest `updated_at` timestamp (last-write-wins).
3. Deletes all losing revisions.

The PostgreSQL ETL process is the final authority. If the server-side truth differs from the client resolution, the next sync cycle overwrites the client version.

## React hooks

| Hook | Returns | Change tracking |
|------|---------|----------------|
| `useProperties(tenantId?)` | `{ properties, isLoading, error, create, update, softDelete, refetch }` | `changes()` with filter function, `live: true` |
| `useProperty(id)` | `{ property, isLoading, error, update, softDelete, refetch }` | `changes()` with `doc_ids: [id]` for single-doc tracking |
| `useSyncStatus()` | `SyncStatus` (`"idle"` \| `"syncing"` \| `"synced"` \| `"error"` \| `"offline"` \| `"retrying"`) | Pub-sub via `onSyncStatusChange()` |
| `useOnline()` | `boolean` | `navigator.onLine` + `window.online`/`offline` events |
