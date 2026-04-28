# ofa-web — Offline-First Property Management Frontend

Next.js 16 frontend for the OFA PMS proof of concept. Uses PouchDB for local-first data storage with automatic CouchDB synchronization.

## Tech stack

- **Framework**: Next.js 16.2 (App Router, Turbopack)
- **Runtime**: React 19.2, TypeScript 5
- **Styling**: Tailwind CSS 4 (CSS-first `@theme`), shadcn/ui
- **Local database**: PouchDB 9 (IndexedDB)
- **Sync**: PouchDB ↔ CouchDB live bidirectional replication
- **Validation**: Zod 4
- **Icons**: @remixicon/react
- **Package manager**: pnpm

## Development

```bash
pnpm install
pnpm dev
```

Open http://localhost:3000. Requires CouchDB running (see root `compose.yaml`).

### Environment variables

Create `.env.local`:

```
NEXT_PUBLIC_COUCHDB_URL=http://admin:admin@localhost:5984
NEXT_PUBLIC_COUCHDB_GLOBAL_DB=globaldb
NEXT_PUBLIC_COUCHDB_SCOPED_DBS=properties,reservations
```

- `NEXT_PUBLIC_COUCHDB_GLOBAL_DB`: a shared database synced by every signed-in session.
- `NEXT_PUBLIC_COUCHDB_SCOPED_DBS`: comma-separated database suffixes that become token-scoped remote DB names.
- Scoped DB naming format: `tenant_code$username$databasename`

Example scoped database names:
- `acme$jdoe$properties`
- `acme$jdoe$reservations`

The client still writes locally to PouchDB `"properties"`; only the remote CouchDB database names are dynamic.

## Data layer

### File structure

```
src/lib/db/
├── index.ts                 # PouchDB instance (auto_compaction, revs_limit: 10)
├── schema.ts                # Zod schemas, TypeScript types, enum definitions
├── property-repository.ts   # CRUD operations with Result<T, E> return types
├── sync.ts                  # CouchDB sync, conflict resolution, status tracking
├── use-properties.ts        # React hook: property list + live changes feed
└── use-property.ts          # React hook: single property + doc-scoped changes feed
```

### PouchDB document conventions

Every document follows this pattern:

```typescript
{
  _id: "property::uuid",       // Prefix + crypto.randomUUID()
  _rev: "1-abc...",             // CouchDB revision (auto-managed)
  type: "property",             // Discriminator for view filtering
  user_id: "7f2b...",           // JWT sub claim
  username: "jdoe",
  tenant_id: "tenant-uuid",
  tenant_code: "acme",
  // ... entity fields
  created_at: "2024-01-15T10:30:00.000Z",
  updated_at: "2024-01-15T10:30:00.000Z",
  deleted_at: null,              // Soft-delete; never uses _deleted: true
}
```

### Repository pattern

All CRUD operations return a `Result<T, E>` discriminated union — they never throw:

```typescript
const result = await propertyRepository.create(input, {
  userId: user.userId,
  username: user.username,
  tenantId: user.tenantId,
  tenantCode: user.tenantCode,
});
if (result.success) {
  // result.data is the created PropertyDoc
} else {
  // result.error has kind + message for specific error handling
}
```

Available methods:

| Method | Returns | Description |
|--------|---------|-------------|
| `create(input, identity)` | `Result<PropertyDoc, CreateError>` | Validates with Zod, generates `_id`, writes user/tenant metadata, defaults status to DRAFT |
| `getAll(tenantId?)` | `Result<PropertyDoc[], ReadError>` | Fetches all non-deleted properties, sorted by `updated_at` desc |
| `getById(id)` | `Result<PropertyDoc, ReadError>` | Accepts both `property::uuid` and raw `uuid` |
| `getByStatus(status, tenantId?)` | `Result<PropertyDoc[], ReadError>` | Filters `getAll` by status |
| `update(id, changes)` | `Result<PropertyDoc, UpdateError>` | Read-merge-write with conflict detection |
| `softDelete(id)` | `Result<PropertyDoc, DeleteError>` | Sets `deleted_at` + status ARCHIVED |

### React hooks

**`useProperties(tenantId?)`** — reactive property list:

```typescript
const { properties, isLoading, error, create, update, softDelete, refetch } =
  useProperties("tenant::demo");
```

Uses PouchDB `changes()` feed with `live: true` for automatic UI updates when local data changes (including from sync).

**`useProperty(id?)`** — single property with live updates:

```typescript
const { property, isLoading, error, update, softDelete, refetch } =
  useProperty("property::abc-123");
```

Uses `doc_ids: [id]` for efficient single-document change tracking.

### Sync configuration

Sync starts automatically when the `SyncStatusIcon` component mounts in the sidebar.

- Health checks use `NEXT_PUBLIC_COUCHDB_URL`
- One global remote DB is optional via `NEXT_PUBLIC_COUCHDB_GLOBAL_DB`
- Scoped remote DBs come from `NEXT_PUBLIC_COUCHDB_SCOPED_DBS`
- Scoped remote DB names are built from JWT claims as `tenant_code$username$databasename`
- The client starts one live sync per configured remote DB target

Each target uses live bidirectional replication:

```typescript
db.sync(remoteDb, {
  live: true,          // Continuous replication
  retry: true,         // Auto-reconnect on failure
  heartbeat: 10_000,   // 10s heartbeat
  back_off_function: (delay) => Math.min(delay * 2, 60_000),
});
```

**Conflict resolution**: Last-write-wins by `updated_at` timestamp. PostgreSQL ETL is the final authority.

## Routing

| Route | Component | Description |
|-------|-----------|-------------|
| `/` | `PropertyClient` | Property list with grid cards and create dialog |
| `/properties/[id]` | `PropertyDetailClient` | Full detail view with edit/delete actions |

All PouchDB-touching pages use `dynamic(() => import(...), { ssr: false })` because PouchDB references the browser `self` global.

Route boundaries:
- `loading.tsx` — skeleton matching the page layout
- `not-found.tsx` — "Property not found" with back link
- `error.tsx` — error boundary with retry button

## Component architecture

```
src/components/
├── layout/
│   ├── app-shell.tsx          # Sidebar + header + footer
│   ├── client-shell.tsx       # SSR-safe wrapper (dynamic import)
│   └── sync-status.tsx        # Lazy-loaded sync indicator
├── properties/
│   ├── property-form.tsx      # Shared form (17 fields) for create + edit
│   ├── create-property-dialog.tsx  # Dialog wrapping PropertyForm
│   ├── edit-property-dialog.tsx    # Dialog with initialData pre-population
│   ├── property-card.tsx      # Card with Link header + action footer
│   ├── confirm-delete-dialog.tsx   # Reusable delete confirmation
│   └── toast-container.tsx    # Toast notifications
└── ui/                        # shadcn/ui primitives
```

**Property form**: The `PropertyForm` component renders all 17 schema fields. It accepts `initialData?: PropertyDoc` — when provided, fields are pre-populated for editing. The form uses HTML `id` attributes so submit buttons can target it via `form="property-create-form"` from outside the form element.

**Property card**: The card header and content are wrapped in `<Link>` to `/properties/[id]`. The footer (status dropdown, delete button) sits outside the link so those actions don't trigger navigation.

## Offline-first UI patterns

| Pattern | Implementation |
|---------|---------------|
| **Offline banner** | Detail page shows amber "Viewing offline — changes save locally" when disconnected |
| **Toast differentiation** | "Saved locally" when offline, "Saved" when online |
| **Sync indicator** | Sidebar footer shows wifi icon with status: Synced, Syncing, Offline, Error |
| **Loading skeletons** | Matching skeleton layouts for both list and detail pages |
| **Error boundaries** | `error.tsx` with retry button catches PouchDB crashes |
| **Not-found state** | `not-found.tsx` handles missing/deleted properties |

## Available scripts

| Command | Description |
|---------|-------------|
| `pnpm dev` | Start development server (Turbopack) |
| `pnpm build` | Production build |
| `pnpm start` | Start production server |
| `pnpm lint` | Run ESLint |
