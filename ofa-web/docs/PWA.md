# PWA — Offline-First Architecture with Serwist

This document describes how the Progressive Web App works: service worker strategy, data flow, and the offline navigation routing mechanism.

## Overview

The app is a Next.js 16 frontend that stores all data locally in the browser using PouchDB. A background process syncs that data with CouchDB when online. The service worker, built with Serwist, handles caching and enables full offline functionality including navigation to any route.

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                              Browser                                    │
│  ┌─────────────┐    ┌─────────────┐    ┌────────────────────────────┐    │
│  │  Service    │    │  PouchDB    │    │      Next.js App           │    │
│  │  Worker     │◄──►│ (IndexedDB) │◄──►│   React Client Pages       │    │
│  │  (Serwist)  │    │             │    │                            │    │
│  └─────────────┘    └─────────────┘    └────────────────────────────┘    │
│         ▲                  ▲                       ▲                      │
│         │                  │                       │                      │
│         ▼                  │                       │                      │
│  ┌─────────────┐           │                       │                      │
│  │   Cache     │           │                       │                      │
│  │  (Static    │           │                       │                      │
│  │   Assets)   │           │                       │                      │
│  └─────────────┘           │                       │                      │
│                            ▼                       │                      │
│                    ┌─────────────┐                │                      │
│                    │  CouchDB    │◄───────────────┘                      │
│                    │  (Remote)   │         RSC fetches                   │
│                    └─────────────┘                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

## Service worker architecture

### Files

- `src/sw.ts` — Service worker source compiled by Serwist
- `src/app/sw/[...path]/route.ts` — Next.js route that serves the compiled service worker

### Precached assets

At build time, Serwist bundles all static assets into a precache manifest. These assets are stored in the Cache Storage API when the service worker installs.

```text
34 precache entries (build output):
  /                    → App shell HTML (contains <ShellClient />)
  /manifest.webmanifest
  /_next/static/...    → JS, CSS, fonts
  /sw/sw.js            → The service worker itself
```

### Runtime caching strategy

Serwist uses Workbox under the hood. The runtime caching is configured in `sw.ts`.

```typescript
runtimeCaching: [
  {
    matcher: ({ request }) => request.mode === "navigate",
    handler: new NetworkFirst({
      cacheName: "navigation",
      plugins: [
        new ExpirationPlugin({ maxEntries: 64, maxAgeSeconds: 24 * 60 * 60 }),
        {
          handlerDidError: async () => {
            return _serwist.matchPrecache("/") ?? Response.error();
          },
        },
      ],
    }),
  },
  ...defaultCache,
]
```

Key behaviors:

| Request type | Strategy | Offline behavior |
|-------------|----------|-----------------|
| `navigate` page loads | `NetworkFirst` | Network fails → serve precached `/` HTML |
| RSC (`?_rsc=...`) | No caching | Return `Response.error()` → trigger navigation fallback |
| Static assets (`/_next/...`) | `CacheFirst` | Served from cache |
| Google Fonts | `CacheFirst` | Served from cache |

### The catch handler

Serwist's catch handler runs when no runtime cache route matches and the network request fails.

```typescript
serwist.setCatchHandler(async ({ request, url }) => {
  if (request.mode === "navigate") {
    const cached = await _serwist.matchPrecache("/");
    if (cached) return cached;
  }

  if (url.searchParams.has("_rsc") || request.headers.get("RSC") === "1") {
    return Response.error();
  }

  if (url.pathname === "/manifest.webmanifest") {
    const cached = await _serwist.matchPrecache("/manifest.webmanifest");
    if (cached) return cached;
  }

  return new Response("Offline", { status: 503 });
});
```

### Why `Response.error()` for RSC?

Next.js App Router uses React Server Component requests to load route data. When the server is unreachable:

- `Response.error()` causes a real network failure, so Next.js falls back to browser navigation.
- `Response(..., { status: 503 })` returns an HTTP response, so Next.js treats it as a valid response and renders an error state instead.

This is why RSC requests must return `Response.error()`, not a `503` response.

## Offline navigation flow

### The problem

Next.js 16 with App Router uses React Server Components for data fetching. When you navigate to `/properties/abc-123`:

1. The Next.js client router sends an RSC request to `/?_rsc=...`.
2. The request goes through the service worker.
3. If the app is offline, the RSC request fails.
4. Next.js must either show an error or fall back to browser navigation.

### The solution: shell client routing

The app uses a single precached app shell that reads the browser URL and renders the correct page client-side.

```text
Step 1: Browser requests /properties/abc-123
         │
Step 2: Service worker intercepts the navigation request
         │
Step 3: NetworkFirst tries the network → FAILS offline
         │
Step 4: handlerDidError returns precached "/" HTML
         │
Step 5: Browser loads the precached shell
         │
Step 6: ShellClient reads window.location.pathname
         │   → "/properties/abc-123"
         │
Step 7: ShellClient renders <PropertyDetailClient>
         │
Step 8: useProperty("abc-123") reads from PouchDB
         │
Step 9: Detail page renders with local data
```

### File structure

```text
src/app/
├── (shell)/
│   ├── page.tsx
│   └── shell-client.tsx
├── (properties)/
│   ├── property-client.tsx
│   └── property-detail-client.tsx
└── conflicts/
    └── conflict-client.tsx
```

`ShellClient` uses `usePathname()` from `next/navigation` to read the real browser URL.

```typescript
export default function ShellClient() {
  const pathname = usePathname();

  if (pathname === "/conflicts") {
    return <ConflictClient />;
  }
  if (pathname.startsWith("/properties/")) {
    const id = pathname.split("/properties/")[1];
    return <PropertyDetailClient params={Promise.resolve({ id })} />;
  }
  return <PropertyClient />;
}
```

### Why this works

Precaching every individual URL would be expensive and brittle. The shell approach keeps the offline path simple:

1. One precached HTML shell at `/`
2. One service worker that handles all routes
3. Client-side route selection based on the real URL
4. Local data reads from PouchDB instead of the server

## Data layer

### Local: PouchDB

```text
PouchDB("properties", { auto_compaction: true, revs_limit: 10 })
  → persisted to IndexedDB in the browser
```

Every document follows this shape:

```typescript
{
  _id: "property::uuid",
  _rev: "1-abc...",
  type: "property",
  tenant_id: "tenant::demo",
  deleted_at: null,
  created_at: "...",
  updated_at: "...",
  // ... property fields
}
```

### Sync: PouchDB ↔ CouchDB

```typescript
db.sync(remoteDb, {
  live: true,
  retry: true,
  heartbeat: 10_000,
  back_off_function: (delay) => Math.min(delay * 2, 60_000),
})
```

## Live sync architecture

```text
┌──────────────────────────────────────────────────────┐
│                    Browser tab                       │
│  ┌────────────────────────────────────────────────┐ │
│  │  PouchDB ("properties")                       │ │
│  │  ┌──────────────────────────────────────────┐  │ │
│  │  │  changes feed (live: true)               │  │ │
│  │  │  → useProperties() re-renders on change  │  │ │
│  │  │  → useProperty(id) re-renders on change  │  │ │
│  │  └──────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────┘ │
│                         ↕ live sync                  │
└──────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────┐
│        CouchDB (from `NEXT_PUBLIC_COUCHDB_URL`)      │
│  ┌────────────────────────────────────────────────┐ │
│  │  _replicator (cluster replication state)       │ │
│  │  checkpoint documents (_local/)                │ │
│  └────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

`SyncProvider` rehydrates the in-memory access token from the `ofa_access_token` httpOnly cookie before it starts sync. `startSync(accessToken)` then uses that token for the CouchDB health check and replication fetches.

If a sync-related authenticated request gets `401` or `403`, the app:

1. clears the stale in-memory access token
2. calls `POST /auth/refresh`
3. rotates both auth cookies on success
4. restores the new access token in memory
5. retries the failed request once

If refresh fails with a definitive auth error, the app clears auth state and stops sync. If refresh fails because of a transient outage, sync falls back to its existing retry and offline behavior.

The current refresh-and-retry behavior is implemented for sync and CouchDB-facing requests. There is not yet a shared authenticated fetch wrapper for every future browser API call.

## Sync connection lifecycle

There are two orthogonal dimensions:

1. **Browser connectivity** via `navigator.onLine`
2. **CouchDB reachability** via the `/_up` health check

The app handles both:

| State | Behavior |
|------|----------|
| Browser offline | Set status to `offline` and wait for the `online` event |
| Browser online + CouchDB unreachable | Retry with exponential backoff |
| Browser online + CouchDB reachable | Start or resume live sync |
| Browser online + auth expired | Refresh once, then retry once |

This is why the sync icon can show `retrying` even when `navigator.onLine === true`.

### Why the health check matters

Without the pre-flight `/_up` check, PouchDB would open a replication handle immediately and discover the connection error later. By checking `/_up` first:

- The UI shows `retrying` immediately.
- No unnecessary replication handle is created.
- Lifecycle retries happen at the app level instead of inside PouchDB internals.
- Auth failures can trigger the one-time refresh flow before replication opens.

### Multiple tabs

Each browser tab has its own PouchDB instance and its own live replication handle. This is fine for a POC, but in production you might want:

- leader election so only one tab owns the CouchDB sync
- `BroadcastChannel` to propagate sync status across tabs
- `SharedWorker` for a single sync process per origin

Currently, concurrent tabs all sync independently. PouchDB and CouchDB handle this correctly via checkpoint documents, but it increases network traffic.

## How the UI re-renders on change

PouchDB's `changes({ live: true, since: "now", include_docs: true })` feed drives the UI. Hooks like `useProperties()` and `useProperty(id)` subscribe to that feed.

```typescript
useEffect(() => {
  const changes = db.changes({
    live: true,
    since: "now",
    include_docs: true,
  }).on("change", async () => {
    const next = await listProperties();
    setState(next);
  });

  return () => changes.cancel();
}, []);
```

When sync delivers a change, the feed fires and the UI re-renders. No page refresh is needed.

## Handling temporary network loss

If the browser goes offline during sync:

1. The `offline` event fires.
2. The app sets status to `offline`.
3. PouchDB replication pauses internally.
4. When the browser comes back online, the `online` event fires.
5. `startSync()` reconnects replication.

The lifecycle retry logic is separate from the browser's online and offline state:

- **Browser offline** → wait for the `online` event
- **Browser online, CouchDB down** → retry with backoff
- **Browser online, auth expired** → refresh once, then retry once

This separation prevents wasted retries while the device is offline.

## Why this works well for offline-first

1. **Local writes are always fast** — `db.put()` writes to IndexedDB immediately.
2. **Sync is opportunistic** — it runs when possible and pauses when not.
3. **Lifecycle retries are bounded** — exponential backoff caps at 60 seconds.
4. **The UI is reactive** — pages subscribe to the PouchDB changes feed.
5. **Offline navigation is reliable** — the app shell can render any route from the cache.
6. **Auth recovery is automatic** — expired access tokens can recover without forcing an immediate re-login when the refresh token is still valid.

## Tradeoffs and limitations

### 1. RSC pages must have a client fallback

Because offline routing always falls back to the cached `/` shell, any route you want to support offline must be rendered by `ShellClient`.

If you add a new page route such as `/bookings/[id]` and forget to handle it in `ShellClient`, offline navigation to that URL falls back to the wrong page.

### 2. No offline SSR data

RSC fetches are not cached. That is intentional. RSC payloads are tied to server state, and cache invalidation is hard. Instead, the app uses:

- SSR or SSG for the initial online load
- PouchDB for offline data
- `ShellClient` for offline route rendering

### 3. Service worker updates need a reload

When `sw.ts` changes:

1. `npm run build` rebundles the service worker.
2. The browser downloads the new service worker.
3. The new worker waits in `installed` state until tabs using the old worker close.
4. A refresh activates it sooner.

During development:

1. Build the app.
2. Refresh to register the new worker.
3. Check DevTools > Application > Service Workers.

### 4. Multiple tabs mean multiple sync processes

Each tab runs its own PouchDB live sync. This is okay for a POC, but not ideal at scale.

## Sync status tracking

`startSync()` performs a health check against `/_up` before opening replication. If CouchDB is unreachable, it schedules a retry with exponential backoff from 1 second up to 60 seconds.

Sync events update a global status that `SyncStatusIcon` reads:

| Status | Meaning |
|--------|---------|
| `idle` | Sync not started or stopped |
| `syncing` | Active data transfer |
| `synced` | Up to date and connection healthy |
| `error` | CouchDB denied a document |
| `offline` | Browser has no network |
| `retrying` | CouchDB unreachable, waiting to reconnect |

## Conflict resolution

CouchDB allows multi-document conflicts. When a conflict is detected:

1. Fetch all revisions of the document.
2. Compare `updated_at` timestamps.
3. Keep the revision with the latest timestamp.
4. Delete all other revisions.

This is last-write-wins, which is appropriate for this POC because PostgreSQL ETL is the ultimate authority.

## Debugging checklist

### "Offline navigation works on `/` but not `/properties/[id]`"

Check:

- Is `ShellClient` handling that path?
- Is the route a client component that reads from PouchDB?
- Is the service worker returning `Response.error()` for RSC requests?

### "Page shows error instead of offline shell"

Check:

- Did the RSC request return `503` instead of `Response.error()`?
- Is the service worker catch handler matching RSC requests correctly?
- Is the `RSC` header or `?_rsc=` parameter present?

### "UI doesn't update after sync"

Check:

- Is the PouchDB `changes()` feed subscribed?
- Is `include_docs: true` set if needed?
- Is React state updated in the `change` handler?
- Did sync actually fire a `change` event?

### "Sync stays retrying forever"

Check:

- Is the browser offline?
- Does the CouchDB `/_up` endpoint respond?
- Is `NEXT_PUBLIC_COUCHDB_URL` correct?
- Is the access token valid?
- Can `/auth/refresh` mint a new access token?

### "Service worker isn't intercepting"

Check:

- Is `SerwistProvider` mounted in `ClientShell`?
- Is `/sw/sw.js` reachable?
- Did the service worker install successfully?
- Is the browser ignoring the service worker because of hard reload or dev settings?

## Examples

### Expired access token during sync

```text
Sync request receives 401
  → refreshAccessTokenClient()
  → refreshAccessTokenAction()
  → POST /auth/refresh
  → rotate cookies
  → restore in-memory access token
  → retry failed request once
```

### Terminal refresh failure

```text
Sync request receives 401
  → refreshAccessTokenClient()
  → refresh returns terminal auth failure
  → clear auth state
  → stop sync
  → return user to login flow
```

### Transient refresh outage

```text
Sync request receives 401
  → refreshAccessTokenClient()
  → refresh endpoint unavailable
  → fall back to existing retry or offline behavior
```

## Testing offline functionality

1. Open DevTools > Application > Service Workers and unregister the existing worker.
2. Refresh to register the new one.
3. Enable **Offline** in DevTools > Network.
4. Navigate to any route.

Expected console behavior:

| Message | Source | Meaning |
|---------|--------|---------|
| `[sync] Browser offline` | `sync.ts` | Sync detected offline state |
| `[sync] Starting live sync → ...` | `sync.ts` | Sync attempt started |
| `GET /?_rsc=... net::ERR_FAILED` | Chrome | RSC fetch failed, which is expected offline |
| `FetchEvent ... resulted in a network error response` | Chrome | Service worker returned `Response.error()`, which is expected |
| `GET .../_local/... 404` | PouchDB | Checkpoint not found, which is normal on first sync |

Expected screen behavior:

- Property list page (`/`) renders from PouchDB data.
- Detail page (`/properties/[id]`) renders from PouchDB data.
- The amber offline banner appears on the detail page.
- No white error screens appear.
- The sync status icon shows `offline`.

## Full data flow summary

```text
Online, first load:
1. Browser requests /properties/abc
2. Next.js serves HTML + JS
3. Service worker installs
4. Client loads, PouchDB opens IndexedDB
5. SyncProvider rehydrates the access token if needed
6. startSync(accessToken) connects to CouchDB
7. useProperty("abc") reads the local document
8. Sync pulls latest changes from CouchDB
9. changes feed fires and the UI re-renders

Offline, subsequent load:
1. Browser requests /properties/abc
2. Service worker serves the precached / HTML shell
3. ShellClient reads pathname = /properties/abc
4. ShellClient renders <PropertyDetailClient>
5. useProperty("abc") reads from IndexedDB
6. Page renders with local data
7. No network is required
```

## Key takeaways

- **Serwist handles app shell caching**, not structured data caching.
- **PouchDB handles data persistence and offline reads**.
- **ShellClient bridges the gap** between one cached HTML shell and many routes.
- **`Response.error()` for RSC requests is essential** for offline routing to work.
- **Live sync plus the changes feed** makes the UI reactive without polling.
- **Health check plus lifecycle retry** makes sync robust.
- **Automatic refresh-and-retry** helps long-lived sessions recover from access-token expiry during sync.

This architecture gives you a true offline-first PWA: not just a cached landing page, but full route navigation and local data access even when the server is unavailable.

## Related auth-aware sync files

- `src/components/providers/sync-provider.tsx`
- `src/features/auth/actions.ts`
- `src/features/auth/refresh-client.ts`
- `src/lib/db/sync.ts`

## Verification status

- Backend login verified
- Backend refresh verified
- Build verified
- Full browser expired-token retry path still pending manual validation

> **Warning:** Full browser verification of the automatic retry path with an intentionally expired or invalid access token still needs a manual end-to-end session test.

> **Warning:** The current implementation deletes the access-token cookie before it attempts refresh. During a temporary refresh outage, route protection can temporarily treat the session as signed out until a later successful login or refresh restores the cookie.

> **Note:** The refresh response does not include `username`, so the app keeps existing user metadata from login.

> **Note:** Concurrent auth failures share a single in-flight refresh request.

> **Note:** `proxy.ts` still checks only cookie presence. Runtime request recovery happens in the sync layer, not in route protection.

> **Note:** Live backend verification confirmed `POST /auth/login` and `POST /auth/refresh` return `200 OK` with the expected payload shapes.

> **Note:** The older direct `http://localhost:5984` examples in this document describe the local CouchDB role in the architecture, not a hard-coded runtime auth pattern.

> **Note:** Tokens remain in `httpOnly` cookies. The app does not persist them to `localStorage` or `sessionStorage`.

> **Note:** The app retries a failed request once after refresh. It does not loop refresh attempts indefinitely.

> **Note:** Automatic refresh runs only when a networked sync request actually receives an auth failure.

> **Note:** The sync flow remains offline-first.
