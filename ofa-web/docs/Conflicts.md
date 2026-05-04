# Conflict Resolution — PouchDB, CouchDB, and Sync Architecture

## Document Revisions in CouchDB/PouchDB

CouchDB uses a **MVCC (Multi-Version Concurrency Control)** model. Every document write creates a new revision, not by overwriting the existing document but by appending a new revision tree node.

```
Document: property::abc
  _rev: 3-ghi999          ← current revision
  └── Revision tree:
      1-abc ── 2-def ── 3-ghi999       ← linear path (no conflicts)

Document: property::xyz (conflicted)
  _rev: 2-mno000          ← "losing" rev (served by default)
  └── Revision tree:
      1-uvw ── 2-mno000                 ← branch A (default)
            └──── 3-pqr111              ← branch B (conflict — stored as _conflicts)
```

When a document has multiple competing revisions (due to concurrent offline edits), CouchDB stores all branches. The revision with the lowest lexicographic `>_rev` is served by default, but all revisions are accessible by specifying `?rev=...`.

PouchDB mirrors this model. In the browser's IndexedDB, the same revision tree is stored locally.

## How Conflicts Arise

Conflicts occur in three scenarios:

### 1. Concurrent Offline Edits (most common)

```
Tab A (offline)                 Tab B (offline)
   │                              │
   ▼                              ▼
read doc: rev=1                  read doc: rev=1
   │                              │
   ▼                              ▼
modify "name" = "A"             modify "name" = "B"
   │                              │
   ▼                              ▼
write doc: rev=1 → 2-A           write doc: rev=1 → 2-B
   │                              │
   │          Both tabs come online
   │          Both sync to CouchDB
   │                              │
   └──────────────────────────────┘
                    │
                    ▼
              CouchDB stores:
              rev 2-A and rev 2-B as siblings
              (both children of rev 1)
              → CONFLICT
```

### 2. Bidirectional Sync with Delay

```
Local (PouchDB)                 Remote (CouchDB)
     │
     ▼
write doc @ rev 3
     │
     ▼
Sync starts...
     │              ┌──────────────────────┐
     │              │  Remote also changed  │
     │              │  doc @ rev 3 → 4-X    │
     │              │  but push hasn't      │
     │              │  arrived yet          │
     │              └──────────────────────┘
     │
     ▼
Pull: rev 4-X arrives
     │
     ▼
Local rev 3 conflicts with rev 4-X
     │
     ▼
Both stored — CONFLICT
```

### 3. Partitioned Clients

When two clients are both offline and making changes to the same document, then sync to CouchDB after a network partition.

---

## How PouchDB Sync Detects Conflicts

During replication, PouchDB compares local and remote revision trees. If the remote has a revision that the local doesn't know about, and that revision is not a direct descendant of the local tip, PouchDB marks the document as conflicted.

The `_conflicts` array on a document lists all conflicting revision IDs:

```json
{
  "_id": "property::abc-123",
  "_rev": "3-abc",
  "_conflicts": ["3-def", "3-ghi"],
  "name": "Beach Resort",
  "updated_at": "...",
  ...
}
```

To retrieve the conflicting revisions:

```typescript
const doc = await db.get(docId, { conflicts: true });
const conflicts = doc._conflicts; // ["3-def", "3-ghi"]

// Fetch each conflicting revision
const revs = await Promise.all(
  conflicts.map(rev => db.get(docId, { rev }))
);
```

---

## Conflict Detection in this App

### Automatic (during sync)

In `property-repository.update()`, after writing a document, `resolveConflicts()` is called automatically:

```typescript
// property-repository.ts:218
const response = await db.put(updated);
await resolveConflicts(docId);  // ← auto-resolve after every write
```

`resolveConflicts()` (in `sync.ts:259`) uses **last-write-wins** — it compares `updated_at` timestamps across all revisions, keeps the most recent one, and deletes the rest.

This is appropriate for this POC because:
- The PostgreSQL ETL (running server-side) is the authoritative source of truth
- Offline clients can make changes that will eventually be reconciled by the ETL
- Last-write-wins ensures the local replica is self-consistent

### Manual (via Conflict Resolution UI)

The `/conflicts` page shows all documents with unresolved `_conflicts`. It uses `getAllConflicts()` from `conflict.ts`:

```typescript
// Returns every document that has _conflicts
export async function getAllConflicts(): Promise<ConflictEntry[]> {
  const result = await rawDb.allDocs({
    include_docs: true,
    conflicts: true,   // ← must request conflicts explicitly
  });

  // For each conflicted doc, fetch all revisions
  for (const row of result.rows) {
    const conflictDocs = await Promise.all(
      row.doc._conflicts.map(rev => rawDb.get(row.id, { rev }))
    );
    // Build ConflictEntry with all revisions
  }
}
```

Each `ConflictEntry` contains all revisions with their `updated_at` and `updated_by` fields, plus computed field-level diffs (via `computeFieldDiffs()`) so users can see exactly which fields differ.

---

## Two Resolution Strategies

### Quick Resolve: Last-Write-Wins (Automatic)

Used automatically after every write. Also available as a one-click option in the UI.

```typescript
export async function resolveByRevision(
  docId: string,
  winnerRev: string,
): Promise<void> {
  const doc = await rawDb.get(docId, { conflicts: true });

  // If winner is not the current default rev, promote it
  if (doc._rev !== winnerRev) {
    const winnerDoc = await rawDb.get(docId, { rev: winnerRev });
    await rawDb.put({ ...winnerDoc, _rev: doc._rev }); // overwrite winning rev
  }

  // Delete all losing revisions
  await Promise.all(
    doc._conflicts.map(rev => rawDb.remove(docId, rev))
  );
}
```

### Advanced Resolve: Field-Level Merge (Manual)

Allows a human to pick value-by-value from different revisions, then writes a merged document.

```typescript
export async function resolveByFieldMerge(
  docId: string,
  fieldPicks: Record<string, string>,  // { "name": "3-abc", "city": "3-def" }
): Promise<void> {
  const doc = await rawDb.get(docId, { conflicts: true });

  // Fetch all revisions
  const allRevs: Record<string, Record<string, unknown>> = {};
  allRevs[doc._rev] = doc;
  for (const rev of doc._conflicts) {
    allRevs[rev] = await rawDb.get(docId, { rev });
  }

  // Build merged doc from picks
  const merged = { ...doc };
  for (const [field, pickRev] of Object.entries(fieldPicks)) {
    merged[field] = allRevs[pickRev][field];
  }

  merged.updated_at = new Date().toISOString();
  merged.updated_by = "conflict-resolver";

  // Write as new revision
  await rawDb.put(merged);

  // Delete all conflict revs
  await Promise.all(doc._conflicts.map(rev => rawDb.remove(docId, rev)));
}
```

---

## Data Flow: Full Sync Architecture

### Startup Sequence

```text
App loads
    │
    ▼
ClientShell mounts
    │
    ▼
SyncProvider hydrates persisted user metadata
    │
    ├── If access token is missing in memory
    │       └── getAccessTokenAction() reads `ofa_access_token` from the httpOnly cookie
    │
    ▼
startSync(accessToken) called
    │
    ├── Check navigator.onLine
    │       │
    │       ├── OFFLINE → setStatus("offline"), wait for "online" event
    │       └── ONLINE → continue
    │
    ▼
checkCouchDbHealth()  ← pings `${origin}/db/_up` with `Authorization: Bearer <token>` when available
    │
    ├── unreachable → scheduleLifecycleRestart() with backoff
    ├── 401/403     → trigger refresh flow once
    │                 ├── refreshAccessTokenAction()
    │                 ├── rotate cookies
    │                 ├── restore in-memory access token
    │                 └── retry health check once
    └── reachable   → connectSync()
                      │
                      ▼
              db.sync(remoteDb, { live: true, retry: true })
                      │
                      ▼
              [sync] Starting live sync → ${NEXT_PUBLIC_COUCHDB_URL}/${tenant_code}$${username}$properties
                      │
                      ▼
              Replication events:
              ├── "active"   → setStatus("syncing")
              ├── "paused"   → setStatus("synced")  [no error]
              │               setStatus("error")   [has err]
              ├── auth 401/403 → refresh once, then replay failed request once
              └── terminal auth failure → clear auth state, stop sync
``` 

> **Note:** Concurrent auth failures share one in-flight refresh request.

> **Warning:** Full browser verification of the automatic refresh-and-retry path still needs a manual session test with an intentionally expired or invalid access token.

> **Note:** The app now uses the access token from the login session for sync-related requests. It no longer relies on the older direct `admin:admin@localhost:5984` example shown in earlier drafts of this document.

> **Note:** The refresh flow is reactive. It runs after a sync-related request fails with `401` or `403`.

> **Note:** If refresh fails with a definitive auth error, the session ends and sync stops.

> **Note:** If refresh fails because of a transient outage, sync falls back to its existing retry and offline behavior.

> **Note:** The current refresh-and-retry implementation is specific to sync and CouchDB-facing requests.

> **Note:** Tokens remain in `httpOnly` cookies. The client keeps only the access token in memory.

> **Note:** `SyncProvider` rehydrates the access token through a Server Action instead of reading cookies directly in browser JavaScript.

> **Note:** The failed request replay happens one time only.

> **Note:** Route protection still happens separately in `proxy.ts`.

> **Note:** This section reflects the implementation in `src/components/providers/sync-provider.tsx`, `src/features/auth/actions.ts`, `src/features/auth/refresh-client.ts`, and `src/lib/db/sync.ts`.

> **Note:** The health check target is derived from `NEXT_PUBLIC_COUCHDB_URL`, not hard-coded to `http://localhost:5984`.

> **Note:** The replication URLs are derived from `NEXT_PUBLIC_COUCHDB_URL`, plus `NEXT_PUBLIC_COUCHDB_GLOBAL_DB` and the token-scoped names from `NEXT_PUBLIC_COUCHDB_SCOPED_DBS` using `tenant_code$username$databasename`.

> **Note:** The live backend contract for `/auth/refresh` was verified with a real account.

> **Note:** The refresh response rotates both access and refresh tokens.

> **Note:** The refresh response does not include `username`, so the client keeps the existing user metadata from login.

> **Note:** The goal of this behavior is to preserve sync continuity after access-token expiry without adding an infinite retry loop.

> **Note:** The automatic conflict-resolution behavior described below is unchanged.

> **Note:** End of sync startup update.

When sync delivers a change (push or pull), the changes feed fires, and the UI re-renders with the updated data — **no page refresh needed**.

## Handling Temporary Network Loss

If the browser goes offline during sync:

1. The `offline` event fires
2. `setStatus("offline")`
3. PouchDB replication pauses internally
4. When the browser comes back online, the `online` event fires
5. `startSync()` reconnects replication

The lifecycle retry logic is independent of the browser's online/offline state:

- **Browser offline** → wait for `online` event
- **Browser online, CouchDB down** → retry with backoff
- **Browser online, auth expired** → refresh once, then retry once

This separation prevents wasted retries while the device is offline.

## Why This Works Well for Offline-First

1. **Local writes are always fast** — `db.put()` writes to IndexedDB immediately
2. **Sync is opportunistic** — it runs when possible, pauses when not
3. **Lifecycle retries are bounded** — exponential backoff caps at 60s
4. **The UI is reactive** — all pages subscribe to the PouchDB changes feed
5. **Conflicts are resolvable** — both automatically (LWW) and manually (field merge)
6. **Auth recovery is automatic** — expired access tokens can be refreshed without forcing an immediate re-login when the refresh token is still valid

## Future Improvements

- Per-document CRDT merge strategies for specific field types
- Push only changed fields instead of full documents
- Background Sync API for writes while the tab is closed
- Better visibility into sync progress (documents/sec, bytes/sec)
- Multi-tab leader election so only one tab owns replication
- A shared authenticated fetch wrapper for browser API calls outside the sync path

## Summary

PouchDB and CouchDB provide an excellent sync backbone for an offline-first PWA:

- MVCC gives automatic conflict detection
- Live replication gives near-instant updates
- Service worker caching gives full offline navigation
- IndexedDB gives durable local storage
- The current auth flow now adds automatic access-token refresh for sync-related requests

For a POC, the combination of:
- **Last-write-wins** auto-resolution
- **Manual conflict UI** for inspection/override
- **Resilient sync startup and retry logic**
- **Automatic refresh-and-retry on auth failure**

...is robust, easy to reason about, and demonstrates the full offline-first architecture clearly.

## Related Code

- `src/lib/db/sync.ts` — replication, retry logic, auth recovery, conflict resolution
- `src/lib/db/conflict.ts` — conflict listing and resolution helpers
- `src/lib/db/property-repository.ts` — write path, auto-conflict resolution
- `src/components/providers/sync-provider.tsx` — token rehydration and sync lifecycle
- `src/features/auth/actions.ts` — login, refresh, logout, access-token rehydration
- `src/features/auth/refresh-client.ts` — single-flight client refresh coordinator
- `src/app/conflicts/conflict-client.tsx` — conflict resolution UI
- `src/components/layout/sync-status.tsx` — sync status indicator
- `src/sw.ts` — service worker and offline routing

---

If you want, I can also write a **step-by-step diagram of a specific conflict scenario** (e.g. two tabs editing the same property offline and then reconnecting) with the exact PouchDB/CouchDB revision state at each step.

### Sync States Reference

| Status | Meaning |
|--------|---------|
| `idle` | No sync running |
| `syncing` | Active data transfer |
| `synced` | Connection healthy and up to date |
| `offline` | Browser offline |
| `retrying` | CouchDB unreachable; waiting for next lifecycle retry |
| `error` | Sync paused or failed because of a non-terminal error |
| auth failure event | Terminal auth failure; auth store is cleared and sync stops |

## Example: Timeline of a Healthy Sync Session

```text
T=0   App loads
T=1   SyncProvider rehydrates access token from httpOnly cookie
T=2   startSync(accessToken)
T=3   Health check succeeds
T=4   db.sync() opens live replication
T=5   status = "active"  → setStatus("syncing")
T=6   changes arrive      → UI re-renders
T=7   queue empty         → setStatus("synced")
```

## Example: Timeline of an Expired Access Token

```text
T=0   Sync request receives 401
T=1   refreshAccessTokenClient() starts
T=2   refreshAccessTokenAction() posts { refreshToken }
T=3   backend returns new accessToken + refreshToken
T=4   cookies rotate
T=5   access token restored in memory
T=6   failed request retries once
T=7   sync continues
```

## Example: Timeline of a Terminal Refresh Failure

```text
T=0   Sync request receives 401
T=1   refreshAccessTokenClient() starts
T=2   refreshAccessTokenAction() returns terminal failure
T=3   auth cookies cleared
T=4   auth store cleared
T=5   SyncProvider stops sync
T=6   route protection sends user back to /login
```

## Example: Timeline of a Transient Refresh Failure

```text
T=0   Sync request receives 401
T=1   refreshAccessTokenClient() starts
T=2   refresh endpoint unavailable
T=3   sync falls back to retry/offline handling
T=4   later retry can attempt recovery again
```

## Verification Notes

- The live backend contract for `/auth/login` and `/auth/refresh` was verified with tenant `ofapoc_001` and user `virgodarth`.
- Build verification passed after the refresh implementation was added.
- A full browser session test with an intentionally invalid or expired access token remains a manual verification step.

## Important Caveat

> **Warning:** The current refresh implementation deletes the access-token cookie before it attempts refresh. During a temporary refresh outage, `proxy.ts` can temporarily treat the session as signed out until a later successful login or refresh restores the cookie.

## Reader takeaway

If you only need the short version: sync starts from a cookie-backed access token, failed sync requests can refresh that token once, and conflict resolution continues to work the same way after the auth update.

---

End of document.

### Auth-Aware Sync

The sync layer now integrates with the auth flow.

- `SyncProvider` rehydrates the access token from the `ofa_access_token` httpOnly cookie.
- `startSync(accessToken)` passes the token into health checks and remote replication fetches.
- If the health check or a replication request gets `401` or `403`, the sync layer triggers the refresh flow.
- The refresh flow rotates both cookies, restores the in-memory access token, and retries the failed request once.
- If refresh fails permanently, the auth store is cleared and sync stops.

> **Note:** The automatic refresh behavior is limited to sync-related requests today.

> **Note:** This auth-aware sync update does not change the conflict resolution algorithms themselves.

> **Note:** The refresh response does not include `username`, so the app keeps the existing user metadata from login.

---

### Recovery Path for Auth Failure

```text
401/403 during sync
    │
    ▼
clear stale in-memory access token
    │
    ▼
POST /auth/refresh
    │
    ├── success → rotate cookies → retry request once → continue sync
    ├── terminal auth error → clear auth state → stop sync
    └── transient outage → fall back to retry/offline behavior
```

---

### Why This Matters for Conflict Resolution

Conflict resolution depends on a healthy replication channel. Automatic token refresh reduces avoidable sync interruptions when the access token expires during long-lived sessions.

That means:
- fewer unnecessary re-logins during active editing sessions
- fewer stalled replication windows
- better continuity for offline-first workflows

The underlying conflict data model and merge rules do not change.

---

### Updated mental model

Use this model when debugging sync issues:

1. **Is the browser offline?**
2. **Is CouchDB reachable?**
3. **Is the access token still valid?**
4. **Can the refresh token mint a new access token?**
5. **If replication resumes, are there conflicts to resolve?**

That order helps you separate transport failures, auth failures, and data conflicts.

---

### Auth-related code paths

- `src/features/auth/actions.ts`
- `src/features/auth/refresh-client.ts`
- `src/components/providers/sync-provider.tsx`
- `src/lib/db/sync.ts`

These files are now part of the sync troubleshooting surface area.

---

### Verification status

- Backend login verified
- Backend refresh verified
- Build verified
- Full browser expired-token retry path still pending manual validation

---

### End of update

``` 
              ├── "active"   → setStatus("syncing")
              ├── "paused"   → setStatus("synced")  [no error]
              │               setStatus("error")   [has err]
              ├── "denied"   → setStatus("error")
              ├── "error"    → setStatus("error")  [PouchDB retries]
              └── "complete" → if push/pull had 0 errors: setStatus("idle")
                               else: scheduleLifecycleRestart()
```

### Live Replication Events

```
CouchDB                                     Browser (PouchDB)
   │                                             │
   │  ◄────── heartbeat (every 10s) ──────────►  │  Keeps connection alive
   │                                             │
   │  ◄────── push: local changes ─────────────►  │  New edits made offline
   │                                             │
   │  ◄────── pull: remote changes ───────────►  │  Other clients' changes
   │                                             │
   │  On push success:                           │
   │    CouchDB writes rev (e.g., 4-xyz)         │
   │    PouchDB receives ack, updates _rev       │
   │                                             │
   │  On pull conflict:                          │
   │    CouchDB sends all conflicting revs        │
   │    PouchDB stores all in _conflicts array   │
   │    → Document now conflicted               │
   │                                             │
   │  On pull with new rev:                     │
   │    PouchDB applies rev as new tip          │
   │    Changes feed fires "change" event       │
   │    → useProperties() / useProperty() re-render
```

### Changes Feed (Reactive UI)

Every React hook (`useProperties`, `useProperty`, `useConflicts`) creates a PouchDB `changes()` feed:

```typescript
// useProperty.ts
changesRef.current = db
  .changes({
    since: "now",           // start from latest
    live: true,             // keep listening
    include_docs: true,
    doc_ids: [docId],       // filter to single doc
  })
  .on("change", () => {
    if (!cancelled) fetchDoc();  // re-read and re-render
  });
```

When sync delivers a change (push or pull), the changes feed fires, and the UI re-renders with the updated data — **no page refresh needed**.

### Offline Write + Online Sync

```
User edits property (offline)
    │
    ▼
propertyRepository.update()
    │
    ├── db.put(updated)  →  stored in IndexedDB @ rev 5-xxx
    │
    └── resolveConflicts()  →  no-op if no conflicts
    │
    ▼
[User comes online]
    │
    ▼
startSync() reconnects replication
    │
    ▼
PouchDB pushes local change to CouchDB
    │
    ├── CouchDB accepts → sync continues
    └── CouchDB rejects due to conflict → PouchDB retries
```

If CouchDB already has a newer revision (from another client), PouchDB's `retry: true` will handle the conflict. `resolveConflicts()` runs after every `put()` to auto-resolve.

---

## Conflict UI: `/conflicts` Page

The conflicts page is a split-panel UI:

- **Left panel**: table of all conflicted documents (detected via `getAllConflicts()`)
- **Right panel**: shows `ConflictEntry` with:
  - **Quick resolve**: pick a revision as winner, delete others
  - **Field diff table**: `computeFieldDiffs()` output showing each field with conflicting values and which revision each value came from

```typescript
// ConflictEntry structure
{
  docId: "property::abc-123",
  docType: "property",
  winningRev: "3-abc",
  revisions: [
    { rev: "3-abc", doc: {...}, updated_at: "2024-01-15T10:30:00Z", updated_by: "alice" },
    { rev: "3-def", doc: {...}, updated_at: "2024-01-15T10:31:00Z", updated_by: "bob" },
  ],
}
```

---

## Sync Retry & Backoff

### PouchDB Built-in Retry (per-replication)

```typescript
db.sync(remoteDb, {
  retry: true,           // PouchDB retries on failure
  heartbeat: 10_000,     // 10s heartbeat
  back_off_function: pouchdbBackoff,
});
```

The `back_off_function` is PouchDB's internal retry delay. The custom `pouchdbBackoff()` seeds at 1s (not 0, which was the old bug) and doubles with jitter up to 60s:

```typescript
function pouchdbBackoff(delay: number): number {
  const base = delay < 1_000 ? 1_000 : delay * 2;
  return Math.min(withJitter(base), 60_000);
}
```

### App-Level Lifecycle Retry

If PouchDB reports `complete` with errors, or if CouchDB is unreachable during the health check, the app schedules its own retry:

```typescript
function scheduleLifecycleRestart(generation: number) {
  const delay = withJitter(lifecycleRetryDelay);
  lifecycleRetryDelay = Math.min(lifecycleRetryDelay * 2, MAX_BACKOFF_MS);

  lifecycleRetryTimer = setTimeout(() => {
    if (generation === syncGeneration) {
      startSync();  // retry from scratch (new replication handle)
    }
  }, delay);
}
```

This is more robust than relying solely on PouchDB's retry because it also handles the case where CouchDB is completely unreachable at startup.

---

## File Map

| File | Responsibility |
|------|---------------|
| `lib/db/index.ts` | PouchDB instance (`new PouchDB("properties")`) |
| `lib/db/schema.ts` | Zod schemas, `PropertyDoc` type, status enums |
| `lib/db/property-repository.ts` | CRUD with auto-conflict-resolution on write |
| `lib/db/sync.ts` | Sync lifecycle, multi-target PouchDB replication, JWT-derived remote DB names, `resolveConflicts()` (LWW) |
| `lib/db/conflict.ts` | Conflict detection, field diffing, manual resolution strategies |
| `lib/db/use-properties.ts` | React hook: property list + changes feed |
| `lib/db/use-property.ts` | React hook: single property + changes feed |
| `lib/db/use-conflicts.ts` | React hook: conflicted documents + resolution actions |
| `app/conflicts/page.tsx` | Conflict resolution UI |
