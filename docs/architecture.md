# Architecture

System architecture for the OFA offline-first Property Management System.

## Audience

Developers joining or maintaining this project. You should understand React, TypeScript, and the basics of document databases before reading this document.

## System overview

```
┌──────────────────────────────────────────────────────────────┐
│  Browser                                                     │
│  ┌─────────┐    ┌─────────┐    ┌───────────────────────┐    │
│  │ Next.js │───▶│ PouchDB │───▶│ IndexedDB (local)     │    │
│  │ React   │    │ (v9)    │    │ - reads: instant      │    │
│  │ (v19)   │    │         │    │ - writes: immediate    │    │
│  └─────────┘    └────┬────┘    │ - offline: full CRUD   │    │
│                      │         └───────────────────────┘    │
│                      │ live sync (bidirectional)            │
└──────────────────────┼──────────────────────────────────────┘
                       │
                ┌──────▼──────┐
                │   CouchDB   │  Sync relay + offline write buffer
                │   (v3.x)    │  http://localhost:5984
                └──────┬──────┘
                       │ ETL
                ┌──────▼──────┐
                │ PostgreSQL  │  Source of truth
                │   (v18)     │  ofa-api (Spring Boot 4)
                └─────────────┘
```

**Data flow**: PostgreSQL is the source of truth. An ETL process handles PostgreSQL-to-CouchDB data transfer. PouchDB synchronizes with CouchDB bidirectionally. The web app reads and writes to PouchDB locally, then syncs when connectivity is available.

**Key principle**: The browser never talks to PostgreSQL or the Spring Boot API directly. All data access goes through PouchDB. This makes the app offline-first by default — reads come from IndexedDB, and writes queue for sync.

## Tech stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | Next.js (App Router, Turbopack) | 16.2 |
| Runtime | React | 19.2 |
| Language | TypeScript | 5 |
| Styling | Tailwind CSS (CSS-first `@theme`) | 4 |
| Components | shadcn/ui (Radix-based) | CLI v4 |
| Local database | PouchDB (IndexedDB adapter) | 9 |
| Sync relay | CouchDB | 3.x |
| Backend API | Spring Boot | 4 |
| Relational DB | PostgreSQL | 18 |
| Validation | Zod | 4 |
| Icons | Remix Icon | latest |
| Package manager | pnpm | latest |
| Infrastructure | Docker Compose | latest |

## Project structure

```
├── compose.yaml                  # Docker Compose (PostgreSQL + CouchDB)
├── scripts/
│   └── init-couchdb.sh           # CouchDB one-time setup (CORS, single-node)
├── .env.example                  # Environment variables template
├── ofa-api/                      # Spring Boot 4 backend (Java)
├── ofa-web/                      # Next.js 16 frontend (TypeScript)
│   ├── src/
│   │   ├── app/                  # Next.js App Router
│   │   │   ├── layout.tsx        # Root layout (fonts, ClientShell)
│   │   │   ├── globals.css       # Tailwind imports + CSS theme
│   │   │   ├── (properties)/     # Route group (no URL segment)
│   │   │   │   ├── property-client.tsx       # List page client component
│   │   │   │   └── property-detail-client.tsx # Detail page client component
│   │   │   ├── properties/
│   │   │   │   └── [id]/
│   │   │   │       ├── page.tsx      # SSR-safe wrapper (dynamic import)
│   │   │   │       ├── loading.tsx   # Skeleton for client-side navigation
│   │   │   │       ├── not-found.tsx # "Property not found" state
│   │   │   │       └── error.tsx     # Error boundary with retry
│   │   │   └── page.tsx          # Home → redirects to property list
│   │   ├── components/
│   │   │   ├── layout/          # App shell, sidebar, sync status
│   │   │   ├── properties/      # Property CRUD components
│   │   │   └── ui/              # shadcn/ui primitives
│   │   ├── hooks/               # React hooks (use-toast, use-mobile)
│   │   └── lib/
│   │       ├── constants.ts     # Shared constants (status styles, type labels)
│   │       ├── utils.ts         # cn() utility
│   │       └── db/              # PouchDB data layer
│   └── package.json
└── docs/                        # Architecture documentation
```

## Routing

| Route | Component | Description |
|-------|-----------|-------------|
| `/` | `PropertyClient` | Property list with grid cards and create dialog |
| `/properties/[id]` | `PropertyDetailClient` | Full detail view with edit/delete actions |

The URL uses the bare UUID (for example, `/properties/abc-123`), not the prefixed `property::abc-123`. The repository layer handles prefix reconstruction internally.

### Route boundaries

Every PouchDB-touching page uses `dynamic(() => import(...), { ssr: false })` because PouchDB references the browser `self` global and crashes during server rendering.

Each route segment has three boundary files:

| File | Purpose |
|------|---------|
| `loading.tsx` | Skeleton matching the page layout — shown during client-side navigation |
| `not-found.tsx` | "Property not found" state with a back link — shown when the ID doesn't exist in local DB |
| `error.tsx` | Error boundary with retry button — catches PouchDB crashes |

## Data layer

The data layer lives in `ofa-web/src/lib/db/` and has six files with clear separation of concerns:

```
src/lib/db/
├── index.ts                 # PouchDB instance + Mango index
├── schema.ts                # Zod schemas, TypeScript types, enum definitions
├── property-repository.ts   # CRUD operations with Result<T, E> return types
├── sync.ts                  # CouchDB sync, conflict resolution, status tracking
├── use-properties.ts        # React hook: property list + live changes feed
└── use-property.ts          # React hook: single property + doc-scoped changes feed
```

### PouchDB instance (`index.ts`)

Creates a single PouchDB instance backed by IndexedDB:

```typescript
export const db = new PouchDB<PropertyDoc>("properties", {
  auto_compaction: true,  // reclaim disk space automatically
  revs_limit: 10,          // keep last 10 revisions for conflict resolution
});
```

Also creates a Mango query index on `type`, `status`, `tenant_id`, `updated_at`, and `deleted_at` — used for filtered queries.

### Schema (`schema.ts`)

Defines the data contract using Zod schemas. The schema is the single source of truth for both validation and TypeScript types — types are inferred with `z.infer`, never handwritten.

**Document convention**: Every document has a `type` discriminator field (currently `"property"`) and an `_id` prefix (`property::`). The prefix enables efficient `allDocs` range queries. The `type` field enables Mango query filtering and filtered replication.

Example document:

```typescript
{
  _id: "property::a1b2c3d4-...",
  _rev: "1-abc...",
  type: "property",
  name: "Grand Hotel",
  property_type: "HOTEL",
  status: "DRAFT",
  description: "",
  address_line1: "",
  city: "",
  country: "",
  tenant_id: "tenant::demo",
  created_at: "2024-01-15T10:30:00.000Z",
  updated_at: "2024-01-15T10:30:00.000Z",
  deleted_at: null,
}
```

**Soft delete**: Documents are never hard-deleted with `_deleted: true`. Instead, the repository sets `deleted_at` to the current timestamp and changes `status` to `ARCHIVED`. All queries filter out documents where `deleted_at` is set.

**ID convention**: `_id` uses the format `{type}::uuid`. The `property::` prefix serves two purposes:

1. **Storage partitioning**: `allDocs({ startkey: "property::", endkey: "property::\uffff" })` retrieves all property documents without a Mango index.
2. **Namespace isolation**: When the schema grows to include rooms, bookings, and guests, each entity type gets its own prefix — no ID collisions across types.

The `type` discriminator field is not redundant with the prefix — it handles logical filtering (Mango queries, filtered replication) while the prefix handles storage-level partitioning.

### Repository (`property-repository.ts`)

All CRUD operations return a `Result<T, E>` discriminated union. Services never throw — callers check `result.success`:

```typescript
const result = await propertyRepository.create(input, tenantId);
if (result.success) {
  // result.data is the created PropertyDoc
} else {
  // result.error has kind + message for specific error handling
}
```

Error types are discriminated by `kind`:

| Method | Returns | Error kinds |
|--------|---------|-------------|
| `create(input, tenantId)` | `Result<PropertyDoc, CreateError>` | `VALIDATION`, `DB_ERROR` |
| `getAll(tenantId?)` | `Result<PropertyDoc[], ReadError>` | `NOT_FOUND`, `DB_ERROR` |
| `getById(id)` | `Result<PropertyDoc, ReadError>` | `NOT_FOUND`, `DB_ERROR` |
| `getByStatus(status, tenantId?)` | `Result<PropertyDoc[], ReadError>` | `NOT_FOUND`, `DB_ERROR` |
| `update(id, changes)` | `Result<PropertyDoc, UpdateError>` | `NOT_FOUND`, `VALIDATION`, `INVALID_TRANSITION`, `ACTIVATION_REQUIREMENTS`, `CONFLICT`, `DB_ERROR` |
| `softDelete(id)` | `Result<PropertyDoc, DeleteError>` | `NOT_FOUND`, `CONFLICT`, `DB_ERROR` |

The `getById` method accepts both prefixed IDs (`property::uuid`) and bare UUIDs — it reconstructs the prefix internally. This lets URLs use clean bare UUIDs while the database uses prefixed IDs.

The `update` method uses a read-merge-write pattern: it reads the current document, merges changes, stamps `updated_at`, preserves immutable fields (`_id`, `type`, `tenant_id`, `created_at`), and writes back with the current `_rev` for conflict detection.

### Sync (`sync.ts`)

Sync starts when the `SyncStatusIcon` component mounts in the sidebar footer. Configuration:

```typescript
db.sync(remoteDb, {
  live: true,          // continuous replication
  retry: true,         // auto-reconnect on failure
  heartbeat: 10_000,   // 10-second heartbeat
  back_off_function: (delay) => Math.min(delay * 2, 60_000),
});
```

**Sync status** uses a publish-subscribe pattern. Components subscribe with `onSyncStatusChange(callback)` and receive status updates: `idle`, `syncing`, `synced`, `error`, or `offline`.

**Network listeners** are registered exactly once using a `networkListenersAttached` guard flag. The `window.online` event restarts sync after the browser regains connectivity.

**Conflict resolution**: Last-write-wins by `updated_at` timestamp. When a document has conflicting revisions, `resolveConflicts(docId)` fetches all revisions, picks the one with the latest `updated_at`, and deletes the losers. PostgreSQL ETL is the final authority — the client resolves locally but the server can override.

### React hooks

**`useProperties(tenantId?)`** — reactive property list:

```typescript
const { properties, isLoading, error, create, update, softDelete, refetch } =
  useProperties("tenant::demo");
```

Uses PouchDB `changes()` with `live: true` and a filter function for automatic UI updates. The changes feed reference is stored in a `useRef` for safe cleanup on unmount.

**`useProperty(id)`** — single property with live updates:

```typescript
const { property, isLoading, error, update, softDelete, refetch } =
  useProperty("abc-123");
```

Uses `doc_ids: [id]` for efficient single-document change tracking — only watches changes to the specific document, not the entire database.

**`useSyncStatus()`** — sync state for the sidebar indicator. Starts sync on mount, stops on unmount.

**`useOnline()`** — network connectivity state from `navigator.onLine`. Used to show the offline banner and differentiate toast messages ("Saved locally" vs "Saved").

## Component architecture

```
src/components/
├── layout/
│   ├── app-shell.tsx          # Sidebar + header + footer composition
│   ├── client-shell.tsx       # SSR-safe wrapper (dynamic import)
│   └── sync-status.tsx        # Sync status icon in sidebar footer
├── properties/
│   ├── property-form.tsx      # Shared form (17 fields) for create + edit
│   ├── create-property-dialog.tsx  # Dialog wrapping PropertyForm
│   ├── edit-property-dialog.tsx    # Dialog with initialData pre-population
│   ├── property-card.tsx      # Card with Link header + action footer
│   ├── confirm-delete-dialog.tsx   # Delete confirmation dialog
│   └── toast-container.tsx    # Toast notifications
└── ui/                        # shadcn/ui primitives
```

### Rendering strategy

```
layout.tsx (Server Component)
  └── ClientShell (client boundary, dynamic import with ssr: false)
        └── AppShell (client component)
              ├── AppSidebar
              │     └── SyncStatusIcon (lazy-loaded)
              ├── AppHeader
              ├── {children}  ← page content
              └── AppFooter
```

The `ClientShell` component exists as an SSR isolation boundary. It dynamically imports `AppShell` with `{ ssr: false }` to prevent PouchDB from crashing during server rendering. The root `layout.tsx` stays a Server Component — it only imports fonts and metadata.

### Shared form pattern

The `PropertyForm` component renders all 17 schema fields. It accepts `initialData?: PropertyDoc` — when provided, fields are pre-populated for editing. The form uses an HTML `id` attribute (`property-create-form` or `property-edit-form-{id}`) so submit buttons can target it via the `form` attribute from outside the `<form>` element (for example, from the dialog footer).

### Property card navigation

The card header and content are wrapped in a Next.js `<Link>` to `/properties/{uuid}`. The footer (status dropdown, delete button) sits outside the link so those actions don't trigger navigation.

## Offline-first patterns

| Pattern | Implementation |
|---------|---------------|
| **Offline banner** | Detail page shows an amber banner: "Viewing offline — changes save locally" when `!isOnline` |
| **Toast differentiation** | "Saved locally" when offline, "Saved" when online |
| **Sync indicator** | Sidebar footer shows a wifi icon with status: Synced, Syncing, Offline, Error |
| **Loading skeletons** | Matching skeleton layouts for both list and detail pages |
| **Error boundaries** | `error.tsx` with retry button catches PouchDB crashes |
| **Not-found state** | `not-found.tsx` handles missing or deleted properties |
| **Local-first writes** | All writes go to PouchDB (IndexedDB) first, sync when online |

### Offline scenarios

| Scenario | What happens |
|----------|-------------|
| **Normal operation** | PouchDB reads/writes IndexedDB locally. Sync runs in background. |
| **Network drops** | All CRUD operations continue against local IndexedDB. Status indicator shows "Offline". |
| **Network returns** | Sync resumes automatically. Changes upload to CouchDB. |
| **Conflict** | Last-write-wins by `updated_at`. PostgreSQL ETL is the final authority. |
| **Edit while offline** | Writes to local IndexedDB. Toast shows "Saved locally". Syncs when online. |
| **Hard refresh while offline** | App shell not cached (no service worker). This is a known gap — all pages are equally affected. |
| **Document not in local DB** | `useProperty` returns `property: null` → detail page shows "Not found" with a back link. |

> **Note**: The app does not currently use a service worker. Adding Serwist for app shell caching is a separate task. For this POC, the data layer (PouchDB) handles offline correctly and the UI shows proper states for all offline scenarios.

## Infrastructure

Docker Compose runs three services:

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `ofa-postgres` | `postgres:18.3-alpine` | 5432 | Source of truth for ofa-api |
| `couchdb` | `couchdb:latest` | 5984 | PouchDB sync target |
| `couchdb-init` | `curlimages/curl:latest` | — | One-time CouchDB setup (single-node cluster, CORS) |

Both databases use named Docker volumes (`pgdata`, `couchdata`) for data persistence.

CouchDB is configured with CORS enabled and single-node cluster mode by the `couchdb-init` container, which runs `scripts/init-couchdb.sh` once and exits.

### Credentials (development only)

| Service | User | Password |
|---------|------|----------|
| PostgreSQL | `postgres` | `postgres` |
| CouchDB | `admin` | `admin` |

### Useful URLs

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| CouchDB Fauxton UI | http://localhost:5984/_utils |
| PostgreSQL | `localhost:5432` |
