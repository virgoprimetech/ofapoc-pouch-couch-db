# PouchDB and CouchDB sync

Technical reference for the browser-to-server sync layer. Covers initialization, retry/backoff, conflict detection, resolution, and SSR handling.

## Audience

Frontend engineers working on `ofa-web/src/lib/db/`. Read this before modifying `sync.ts`, `conflict.ts`, `sync-provider.tsx`, or any conflict resolution UI components.

Prerequisites: read [architecture.md](architecture.md) for the full system overview and [data-model.md](data-model.md) for document structure.

## File reference

| File | Purpose |
|------|---------|
| `src/lib/db/index.ts` | PouchDB instance creation, Mango index setup |
| `src/lib/db/schema.ts` | Zod schemas, `DocType` union, `docTypeFromId()` helper |
| `src/lib/db/sync.ts` | Live sync lifecycle, retry/backoff, status pub-sub, conflict auto-resolution |
| `src/lib/db/conflict.ts` | Conflict detection, field-level diffing, manual resolution (quick + field merge) |
| `src/lib/db/property-repository.ts` | CRUD operations with `updated_by` audit trail |
| `src/lib/db/use-conflicts.ts` | React hook for the conflict resolution page |
| `src/lib/db/use-properties.ts` | React hook for reactive property list with live changes feed |
| `src/components/providers/sync-provider.tsx` | React context provider that starts sync on mount |
| `src/components/layout/client-shell.tsx` | SSR boundary that dynamically imports SyncProvider with `ssr: false` |
| `src/components/layout/sync-status.tsx` | Sidebar sync status icon |
| `src/components/conflicts/` | Conflict resolution UI components (table, detail panel, side-by-side, field merge) |

## Sync initialization

Sync starts when `SyncProvider` mounts. The provider wraps the entire app inside `ClientShell`:

```
layout.tsx (Server Component)
  └── ClientShell (dynamic import, ssr: false)
        └── SyncProvider
              ├── ensureIndex()
              ├── onSyncStatusChange(setStatus)
              └── startSync()
                    └── AppShell → {children}
```

`SyncProvider` runs three actions on mount:

1. **`ensureIndex()`** — creates the Mango query index on first data access. Idempotent — skips if the index already exists.
2. **`onSyncStatusChange(setStatus)`** — subscribes to the module-level status pub-sub. Receives the current status immediately.
3. **`startSync()`** — cancels any existing sync handle, creates a new remote PouchDB connection, starts bidirectional live replication.

On unmount, the cleanup function calls `stopSync()` and unsubscribes from status changes. This pattern is safe for React StrictMode: mount → cleanup → mount runs `startSync()` → `stopSync()` → `startSync()`, which cancels and restarts cleanly.

## Sync configuration

```typescript
// sync.ts
db.sync(remoteDb, {
  live: true,           // Continuous replication — stays open for real-time changes
  retry: true,          // PouchDB auto-reconnects on transient errors
  heartbeat: 10_000,    // 10-second heartbeat to detect stale connections
  back_off_function: pouchdbBackoff,
});
```

| Option | Value | Purpose |
|--------|-------|---------|
| `live` | `true` | Keeps the `_changes` feed open. Emits `active` when data transfers and `paused` when up to date. |
| `retry` | `true` | On error, PouchDB retries the `_changes` feed using `back_off_function` for the delay. Does not fire `complete`. |
| `heartbeat` | `10000` | Milliseconds between heartbeat pings. If no response within this window, PouchDB treats the connection as dead and retries. |
| `back_off_function` | `pouchdbBackoff` | Custom function that seeds at 1 second, doubles with jitter, caps at 60 seconds. |

The remote database URL is constructed from environment variables:

```
NEXT_PUBLIC_COUCHDB_URL=http://admin:admin@localhost:5984
```

PouchDB appends the database name (`properties`), producing the full URL: `http://admin:admin@localhost:5984/properties`.

## Retry and backoff strategy

The sync module implements two independent retry layers:

### Layer 1: PouchDB internal retry

PouchDB's `retry: true` combined with `back_off_function` handles transient errors in the `_changes` feed. When an error occurs:

1. PouchDB calls `back_off_function(previousDelay)` to get the next delay.
2. It waits that duration, then retries the `_changes` feed.
3. On successful data transfer, PouchDB resets the delay to 0.

The custom `pouchdbBackoff` function:

```typescript
function pouchdbBackoff(delay: number): number {
  const base = delay < 1000 ? 1000 : delay * 2;
  return Math.min(withJitter(base), 60000);
}
```

| Retry | Delay |
|-------|-------|
| 1st | ~1 second |
| 2nd | ~2 seconds |
| 3rd | ~4 seconds |
| 4th | ~8 seconds |
| 5th | ~16 seconds |
| 6th | ~32 seconds |
| 7th+ | ~60 seconds (cap) |

`withJitter` applies ±20% randomization to prevent all clients from retrying simultaneously (thundering herd).

### Layer 2: Lifecycle retry

If the sync handle fires `complete` without an explicit `stopSync()` call (unexpected completion), the module schedules a restart with exponential backoff:

```typescript
function scheduleLifecycleRestart(generation: number) {
  const delay = withJitter(lifecycleRetryDelay);  // 1s → 2s → 4s → ... → 60s
  lifecycleRetryTimer = setTimeout(() => {
    if (generation === syncGeneration) startSync();
  }, delay);
  lifecycleRetryDelay = Math.min(lifecycleRetryDelay * 2, 60000);
}
```

Both layers reset when sync reaches a healthy `paused` state (up to date, no errors). The lifecycle retry also resets on `stopSync()`, `online`, and `offline` events.

### Generation counter

All event handlers capture a `generation` value from `++syncGeneration` at sync start. When `stopSync()` increments the generation, any pending events from the old sync handle are silently discarded. This prevents stale events from overwriting the current sync status.

## Sync status state machine

```
         startSync()
idle ──────────────▶ syncing
  ▲                   │    │
  │           paused   │    │ active
  │          (no err)  │    │
  │                   ▼    ▼
  │                 synced  syncing
  │                   │
  │            paused (err)
  │                   │
  │                   ▼
  │                  error ──▶ (PouchDB retries internally)
  │
  │         browser offline
  ├──────────────── offline
  │
  │     unexpected complete
  ├──────────────── retrying ──▶ startSync()
  │
stopSync()
```

| Status | Meaning | UI indicator |
|--------|---------|-------------|
| `idle` | Sync has not started or was stopped | Gray wifi icon, "Idle" |
| `syncing` | Data transfer is active | Spinning refresh icon, "Syncing" |
| `synced` | All changes replicated, connection paused (healthy) | Green wifi icon, "Synced" |
| `error` | Sync error occurred. With `retry: true`, PouchDB retries automatically. | Red wifi-off icon, "Error" |
| `offline` | Browser detected no network connectivity | Red wifi-off icon, "Offline" |
| `retrying` | Sync completed unexpectedly and is waiting to restart | Amber spinning refresh icon, "Retrying" |

Components subscribe to status changes via `onSyncStatusChange(callback)`. The `SyncStatusIcon` component in the sidebar footer renders the appropriate icon and label for each state.

## Network reconnection

The module registers `window.online` and `window.offline` listeners exactly once (guarded by `networkListenersAttached`):

| Event | Action |
|-------|--------|
| `offline` | Resets lifecycle retry timer. Sets status to `offline`. |
| `online` | Resets lifecycle retry timer. Calls `startSync()` to re-establish the connection. |

`startSync()` also checks `navigator.onLine` before attempting a connection. If the browser reports offline, it sets status to `offline` and returns early without creating a remote PouchDB instance.

## Conflict detection

When two clients edit the same document while offline, CouchDB stores both revisions as conflicting branches. The winning revision is determined by CouchDB's deterministic algorithm (lexicographic `_rev` comparison). The losing revisions appear in the `_conflicts` array.

### Automatic detection

`getAllConflicts()` in `conflict.ts` scans the local database:

```typescript
const result = await rawDb.allDocs({ include_docs: true, conflicts: true });
```

For each document with a non-empty `_conflicts` array, it fetches all conflicting revisions and returns a `ConflictEntry`:

```typescript
interface ConflictEntry {
  docId: string;           // e.g., "property::a1b2c3d4-..."
  docType: string;         // e.g., "property"
  winningRev: string;      // The current winning revision
  revisions: ConflictRevision[];  // All conflicting revisions with metadata
}
```

The scan is type-agnostic — it checks all documents regardless of `_id` prefix. The `docType` is extracted from the `type` field or inferred from the `_id` prefix using `docTypeFromId()`.

### Field-level diffing

`computeFieldDiffs(revisions)` compares all revisions field-by-field and returns an array of `FieldDiff` objects:

```typescript
interface FieldDiff {
  field: string;
  hasConflict: boolean;  // true if values differ across revisions
  values: { rev: string; value: unknown; updated_by: string }[];
}
```

System fields (`_id`, `_rev`, `_conflicts`, `_attachments`) are excluded from comparison. Conflicting fields sort first, then alphabetically.

## Conflict resolution

The system supports two resolution modes, exposed through the `/conflicts` page:

### Quick resolution: pick a winner

`resolveByRevision(docId, winnerRev)` deletes all losing revisions:

1. Fetch the document with `{ conflicts: true }`.
2. If the chosen winner is a conflict revision (not the current winning rev), promote it by writing its content with the winning `_rev`.
3. Delete all other conflict revisions with `db.remove()`.

Use this when one revision is clearly correct and the others should be discarded.

### Advanced resolution: field-level merge

`resolveByFieldMerge(docId, fieldPicks)` builds a merged document from per-field choices:

1. Fetch all revisions (winning + conflicts).
2. For each field, take the value from the revision specified in `fieldPicks`:
   ```typescript
   // Example: take "name" from rev A, "city" from rev B
   { "name": "1-abc...", "city": "2-def..." }
   ```
3. Write the merged document as an update to the winning revision.
4. Delete all conflict revisions.

The merged document gets `updated_at` set to the current time and `updated_by` set to `"conflict-resolver"`.

### Auto-resolution

`resolveConflicts(docId)` in `sync.ts` provides automatic last-write-wins resolution:

1. Fetch all revisions of the document.
2. Pick the revision with the latest `updated_at` timestamp.
3. Delete all losing revisions.

This runs automatically after property updates in the repository. For the manual conflict resolution page, auto-resolution is disabled — the user resolves conflicts through the UI.

## SSR handling

PouchDB requires browser globals (`self`, `IndexedDB`, `fetch`) and cannot run during Next.js server-side rendering. The project uses three mechanisms to prevent PouchDB from executing on the server:

### 1. Dynamic imports with `ssr: false`

```typescript
// client-shell.tsx
const SyncProvider = dynamic(
  () => import("@/components/providers/sync-provider").then((mod) => ({
    default: mod.SyncProvider,
  })),
  { ssr: false },
);
```

`ClientShell` dynamically imports both `SyncProvider` and `AppShell` with `ssr: false`. The root layout stays a Server Component.

### 2. Server external packages

```typescript
// next.config.ts
serverExternalPackages: ["pouchdb-browser", "pouchdb-find"],
```

Tells Next.js to not bundle PouchDB on the server. Instead, it resolves the package from `node_modules` at runtime — which never happens because the dynamic imports prevent server execution.

### 3. Webpack and Turbopack aliases

```typescript
// next.config.ts — webpack (production builds)
webpack: (config, { isServer }) => {
  if (isServer) {
    config.resolve.alias = {
      ...config.resolve.alias,
      "pouchdb-browser": false,
      "pouchdb-find": false,
    };
  }
  return config;
},

// Turbopack (development)
turbopack: {
  resolveAlias: {
    "pouchdb-browser": { browser: "pouchdb-browser" },
  },
},
```

On the server, webpack resolves PouchDB imports to `false` (empty module). In Turbopack, the browser alias ensures PouchDB only loads in the browser context.

## CouchDB setup

CouchDB runs as a Docker container configured by `scripts/init-couchdb.sh`:

1. **Single-node cluster**: Finishes cluster setup for development.
2. **CORS**: Enables cross-origin requests from the browser with `origins: *`, `credentials: true`, and standard HTTP methods/headers.

> **Warning**: The wildcard CORS configuration is for development only. Production deployments must restrict `origins` to the actual frontend domain.

The script is idempotent — safe to run on every `docker compose up`.

## Debugging

All sync events log to the browser console with the `[sync]` prefix:

| Log message | Meaning |
|-------------|---------|
| `[sync] Starting live sync → http://...` | New sync handle created |
| `[sync] Data transfer active` | Documents are being replicated |
| `[sync] Up to date — paused` | All changes synced, connection idle |
| `[sync] Paused with error: ...` | Sync paused due to an error |
| `[sync] Sync error (PouchDB will retry): ...` | Transient error, retry scheduled |
| `[sync] Sync completed normally` | Sync stopped by `stopSync()` |
| `[sync] Unexpected completion — restarting in ~Xs` | Lifecycle retry triggered |
| `[sync] Browser offline — deferring sync until online` | Offline detected at startup |
| `[sync] Browser went offline` | `offline` event fired |
| `[sync] Browser back online — restarting sync` | `online` event fired |

Conflict operations log with `[conflict]` prefix:

| Log message | Meaning |
|-------------|---------|
| `[conflict] Resolved {docId}: kept rev {rev}` | Quick resolution completed |
| `[conflict] Field-merged {docId}` | Advanced field merge completed |
| `[conflict] Failed to resolve {docId}: ...` | Resolution error |

### Common issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| No `[sync]` logs in console | SyncProvider not mounting | Check that `ClientShell` wraps children in the root layout |
| `[sync] Sync error` repeatedly | CouchDB unreachable or CORS not configured | Verify `docker compose up` and check `http://localhost:5984/_up` |
| `self is not defined` | PouchDB imported on server | Verify `ssr: false` dynamic import and `next.config.ts` aliases |
| Conflicts not appearing on `/conflicts` page | Auto-resolution removing them before page load | Check that `sync-provider.tsx` does not call `resolveAllConflicts()` |
| Sync works but data doesn't update in UI | Changes feed not running | Check `useProperties` hook — it subscribes to `db.changes({ live: true })` |

### Manual verification

1. Open two browser tabs with the same property.
2. In tab 1, edit the property name while offline (DevTools → Network → Offline).
3. In tab 2, edit the same property's city while offline.
4. Go online in both tabs. Sync replicates both changes, creating a conflict.
5. Navigate to `/conflicts` — the conflict should appear with both revisions.
6. Resolve using quick or advanced mode.
7. Verify the winning data persists in the property detail page.
