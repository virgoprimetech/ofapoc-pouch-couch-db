# ADR-005: Document-to-relational mapping

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-04-06 |
| **Decision makers** | Engineering team |

## Context

CouchDB stores property documents as JSON with flat key-value structure. PostgreSQL stores them as typed rows. The ETL transform layer must convert between these representations without data loss, handling differences in ID format, timestamp types, null semantics, and revision tracking.

## Decision

### Field mapping table

| CouchDB field | PostgreSQL column | Type | Nullable | Notes |
|---|---|---|---|---|
| `_id` | `id` | `UUID` | No | Strip `property::` prefix |
| `_rev` | `couchdb_rev` | `VARCHAR(100)` | Yes | Stored for reverse sync; not domain model |
| `type` | *(not stored)* | — | — | Always `"property"`; implied by table |
| `name` | `name` | `VARCHAR(255)` | No | Required |
| `property_type` | `property_type` | `VARCHAR(30)` | No | Enum: HOTEL, RESORT, MOTEL, BNB, VACATION_RENTAL, HOSTEL, GUESTHOUSE, APARTMENT_HOTEL, BOUTIQUE_HOTEL, SERVICED_APARTMENT, OTHER |
| `status` | `status` | `VARCHAR(30)` | No | Default `DRAFT`; enum: DRAFT, ACTIVE, SUSPENDED, CLOSED, ARCHIVED |
| `description` | `description` | `TEXT` | Yes | `""` → `NULL` |
| `address_line1` | `address_line1` | `VARCHAR(500)` | Yes | `""` → `NULL` |
| `address_line2` | `address_line2` | `VARCHAR(500)` | Yes | `""` → `NULL` |
| `city` | `city` | `VARCHAR(255)` | Yes | `""` → `NULL` |
| `state_province` | `state_province` | `VARCHAR(255)` | Yes | `""` → `NULL` |
| `postal_code` | `postal_code` | `VARCHAR(20)` | Yes | `""` → `NULL` |
| `country` | `country` | `VARCHAR(2)` | Yes | ISO 3166-1 alpha-2; `""` → `NULL` |
| `latitude` | `latitude` | `DECIMAL(10,8)` | Yes | Preserved as-is |
| `longitude` | `longitude` | `DECIMAL(11,8)` | Yes | Preserved as-is |
| `phone` | `phone` | `VARCHAR(50)` | Yes | `""` → `NULL` |
| `email` | `email` | `VARCHAR(255)` | Yes | `""` → `NULL` |
| `website` | `website` | `VARCHAR(500)` | Yes | `""` → `NULL` |
| `timezone` | `timezone` | `VARCHAR(50)` | Yes | IANA; `""` → `NULL` |
| `currency` | `currency` | `VARCHAR(3)` | Yes | ISO 4217; `""` → `NULL` |
| `total_rooms` | `total_rooms` | `INTEGER` | Yes | Preserved as-is |
| `tenant_id` | `tenant_id` | `VARCHAR(255)` | No | Multi-tenancy partition key |
| `created_at` | `created_at` | `TIMESTAMPTZ` | No | Immutable after create |
| `updated_at` | `updated_at` | `TIMESTAMPTZ` | No | Updated on every write |
| `deleted_at` | `deleted_at` | `TIMESTAMPTZ` | Yes | Soft-delete marker; `null` = active |
| *(computed)* | `version` | `BIGINT` | No | JPA `@Version` for optimistic locking |

### ID convention

- **CouchDB**: `_id = "property::" + UUID` (e.g., `property::a1b2c3d4-5678-...`)
- **PostgreSQL**: `id = UUID` (bare, no prefix)
- **Forward transform**: `doc._id.replace("property::", "")` → `UUID.fromString(...)`
- **Reverse transform**: `"property::" + entity.getId().toString()`

This matches the frontend convention where URLs use bare UUIDs and the data layer reconstructs the prefix.

### Timestamp handling

- **CouchDB**: ISO 8601 strings with `Z` suffix (`"2024-01-15T10:30:00.000Z"`)
- **PostgreSQL**: `TIMESTAMPTZ` columns
- **Java**: `java.time.Instant` — `Instant.parse()` handles ISO 8601 natively
- **Conflict comparison**: `Instant.isAfter()` / `Instant.isBefore()`

### Empty string handling

CouchDB uses `""` for unset optional fields. PostgreSQL stores `NULL`.

- **Forward**: `""` → `NULL` for all optional string fields
- **Reverse**: `NULL` → `""` to preserve round-trip fidelity

### `_rev` tracking

`couchdb_rev` stores the CouchDB revision token for reverse sync PUT requests. Not part of the domain model — never exposed in REST API DTOs.

### Conflict resolution (forward sync)

Per ADR-002 and ADR-003, when both sides have different values:

1. Compare `updated_at` timestamps
2. If CouchDB is newer or equal → overwrite PG
3. If PG is newer → skip (PG wins)
4. Tiebreaker: PostgreSQL is final authority

## Consequences

### Positive
- Lossless round-trip for all 25 fields
- Liquibase `ddl-auto: validate` catches schema drift at startup
- Domain isolation: `couchdb_rev` is integration-only

### Negative
- Empty string ↔ NULL translation adds per-field transform boilerplate
- Decimal precision: CouchDB stores as IEEE 754 double, PG uses `DECIMAL(10,8)` — possible 8th-decimal rounding
