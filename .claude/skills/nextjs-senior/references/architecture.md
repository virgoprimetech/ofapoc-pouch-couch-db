# Architecture Reference

## Table of Contents
1. [Vertical Slice Architecture](#vertical-slice-architecture)
2. [Offline-First Architecture](#offline-first-architecture)
    - [The Data Layer Stack](#the-data-layer-stack)
    - [TanStack DB as Client Database](#tanstack-db-as-client-database)
    - [PouchDB + CouchDB Sync Pipeline](#pouchdb--couchdb-sync-pipeline)
    - [Sync Engine Selection Guide](#sync-engine-selection-guide)
    - [Conflict Resolution Strategies](#conflict-resolution-strategies)
    - [Optimistic Mutations and Outbox Pattern](#optimistic-mutations-and-outbox-pattern)
3. [PWA + App Shell Architecture](#pwa--app-shell-architecture)
    - [App Shell Pattern](#app-shell-pattern)
    - [Service Worker Caching Layers](#service-worker-caching-layers)
    - [Serwist Configuration Architecture](#serwist-configuration-architecture)
    - [Background Sync for Mutations](#background-sync-for-mutations)
    - [Offline Fallback Strategy](#offline-fallback-strategy)
4. [Next.js 16 Caching Architecture](#nextjs-16-caching-architecture)
5. [Rendering Strategy Decision Framework](#rendering-strategy-decision-framework)

---

## Vertical Slice Architecture

Organizes code by **feature/use-case**, not technical layer. Each slice owns its route, components, store slice, schema, service, queries, and data access with minimal coupling to other slices.

```
src/
  features/
    todos/
      components/           # UI renders only, zero business logic
      hooks/                # Wires store/service to React
      schemas/              # Zod schemas (source of truth for types)
      store/                # Zustand slice (ephemeral UI state only)
      collections/          # TanStack DB collections
      queries/              # TanStack Query queryOptions factories
      actions/              # Next.js Server Actions (mutations)
      services/             # Business logic, domain rules
      types.ts              # Inferred from Zod, never hand-written
    products/
      components/
      schemas/
      collections/
      queries/
      actions/
      services/
  shared/
    db/                     # TanStack DB setup, PouchDB sync engine config
    events/                 # Cross-slice event bus (if needed)
    hooks/                  # Generic hooks (useOnline, useDebounce, useLiveQuery wrappers)
    ui/                     # Base shadcn/ui components
    lib/                    # Utilities (cn(), formatters, etc.)
    providers/              # QueryClient, StoreProvider, Serwist registration
```

### Slice Boundary Rules

1. **Slices import from `shared/`.** Never from another slice's internals.
2. **Cross-slice communication** goes through shared events, URL state, or shared Zustand store — never direct imports.
3. **Each slice has a public API surface.** If another part of the app needs data from this slice, export it explicitly from the slice root — not from internal modules.
4. **Server Actions are slice-owned.** A todo Server Action lives in `features/todos/actions/`, not in a global `actions/` folder.
5. **TanStack DB collections are slice-owned.** The todo collection lives in `features/todos/collections/`, configured with the slice's Zod schema.

### Feature Folder Anatomy

```ts
// features/todos/schemas/todo.ts
export const TodoSchema = z.object({ /* ... */ });
export type Todo = z.infer<typeof TodoSchema>;

// features/todos/queries/todo-queries.ts
export const todoListOptions = () => queryOptions({ /* ... */ });

// features/todos/collections/todo-collection.ts
export const todoCollection = createQueryCollection<Todo>({ /* ... */ });

// features/todos/actions/create-todo.ts
"use server";
export async function createTodo(input: unknown) { /* ... */ }

// features/todos/store/todo-ui-store.ts
export const useTodoUIStore = create<TodoUISlice>()(/* ... */);

// features/todos/services/todo-service.ts
export function filterTodosByPriority(todos: Todo[], priority: Priority): Todo[] { /* ... */ }
```

---

## Offline-First Architecture

### The Data Layer Stack

```
┌─────────────────────────────────────────────────┐
│  UI Components (React)                          │
│  └── useLiveQuery() / useSuspenseQuery()        │
├─────────────────────────────────────────────────┤
│  TanStack DB Collections (client-side database) │
│  └── Live queries, optimistic mutations         │
│  └── SQLite persistence (v0.6)                  │
├─────────────────────────────────────────────────┤
│  TanStack Query (server data fetching layer)    │
│  └── Populates collections from REST/GraphQL    │
│  └── Handles staleTime, gcTime, hydration       │
├─────────────────────────────────────────────────┤
│  Sync Engine (bidirectional data flow)          │
│  └── PouchDB↔CouchDB  OR  ElectricSQL/PowerSync│
├─────────────────────────────────────────────────┤
│  Serwist Service Worker (asset caching)         │
│  └── App shell, static assets, offline fallback │
│  └── Background sync for failed HTTP mutations  │
└─────────────────────────────────────────────────┘
```

**Critical separation:** Service workers cache **the app itself** (HTML shell, JS bundles, CSS, images). TanStack DB / PouchDB cache **the data**. Never conflate these two layers.

### TanStack DB as Client Database

TanStack DB sits between your UI and your data sources. It normalizes server responses into typed collections, provides reactive live queries with differential dataflow (~0.7ms updates on 100k rows), and makes every mutation optimistic by default.

**Three sync modes by collection size:**

| Mode | When to Use | Behavior |
|---|---|---|
| Eager | < 10,000 rows | Load entire collection upfront, keep in memory |
| On-demand | > 50,000 rows | Load only what live queries request |
| Progressive | 10k–50k rows | Load query subset immediately, full dataset in background |

**Collection type selection:**

| Backend | Collection Type | Notes |
|---|---|---|
| REST API | `createQueryCollection` | Populated via TanStack Query |
| ElectricSQL | `createElectricCollection` | Read-path sync from PostgreSQL |
| PowerSync | `createPowerSyncCollection` | Bidirectional SQLite sync |
| RxDB | `createRxDBCollection` | Reactive document store |
| Local only | `createLocalOnlyCollection` | No server sync, ephemeral |
| localStorage | `createLocalStorageCollection` | Persists across sessions |

**Offline persistence (v0.6):** Enable SQLite-backed persistence to survive app restarts. Works across browser (WASM), React Native, Expo, Node, Electron, Tauri, Capacitor.

**Offline transactions:** Use `@tanstack/offline-transactions` for the outbox pattern — mutations are queued in a durable store and replayed when connectivity returns.

### PouchDB + CouchDB Sync Pipeline

For projects using CouchDB as the backend, PouchDB provides battle-tested bidirectional replication:

```
Browser (PouchDB)  ←→  CouchDB Server
    │                      │
    ├── Write locally       ├── Accept replication
    ├── Queue replication   ├── Resolve conflicts
    ├── Retry on failure    ├── Push changes back
    └── Detect conflicts    └── UUIDv7 auto-IDs (3.5)
```

**Key rules:**
- Write to PouchDB only — never write directly to CouchDB from the browser
- Use `live: true, retry: true` for continuous replication
- Always specify `limit` on `.find()` calls (default is 25 in PouchDB 9)
- Implement conflict resolution per document type (see below)
- Run sync in only one tab — use a leader election mechanism

### Sync Engine Selection Guide

| Scenario | Recommended Engine | Why |
|---|---|---|
| CouchDB backend, document model | **PouchDB 9** | Battle-tested, built-in replication protocol |
| PostgreSQL backend, relational model | **PowerSync** | SQLite-based, bidirectional, Drizzle integration |
| PostgreSQL read-heavy, server-driven sync | **ElectricSQL** | Efficient Shapes API, no write path |
| Greenfield, community momentum | **Zero** (Rocicorp) | Successor to Replicache, strongest 2026 community |
| Real-time collaborative editing | **Yjs** / **Automerge** | CRDT-based, automatic conflict merging |
| Simple REST API, gradual adoption | **TanStack DB + Query** | No backend changes, adopt one query at a time |

### Conflict Resolution Strategies

**Last-Write-Wins (LWW):** Compare `updatedAt` timestamps; most recent wins. Simple but loses concurrent edits.

```ts
function lwwResolve(local: Doc, remote: Doc): Doc {
  return new Date(local.updatedAt) > new Date(remote.updatedAt) ? local : remote;
}
```

**Field-Level Merge:** Merge non-conflicting fields, flag conflicting ones for user resolution.

```ts
function fieldMerge(base: Doc, local: Doc, remote: Doc): Doc {
  const merged = { ...base };
  for (const key of Object.keys(base)) {
    const localChanged = local[key] !== base[key];
    const remoteChanged = remote[key] !== base[key];
    if (localChanged && !remoteChanged) merged[key] = local[key];
    else if (!localChanged && remoteChanged) merged[key] = remote[key];
    else if (localChanged && remoteChanged) {
      // Both changed — flag for user resolution
      merged[key] = remote[key]; // default to server
      merged._conflicts = [...(merged._conflicts || []), key];
    }
  }
  return merged;
}
```

**Operational Transform / CRDT:** For collaborative text editing, use Yjs or Automerge for automatic structural merging.

### Optimistic Mutations and Outbox Pattern

TanStack DB mutations are optimistic by default — the UI updates instantly. If the server rejects, TanStack DB rolls back automatically.

For durable offline writes (surviving app restarts):

```ts
import { createOfflineTransactionQueue } from "@tanstack/offline-transactions";

const offlineQueue = createOfflineTransactionQueue({
  storage: "indexeddb",          // durable across restarts
  maxRetries: 5,
  retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 30_000),
  onOnline: async (transactions) => {
    // Replay queued mutations in order
    for (const tx of transactions) {
      await fetch(tx.url, { method: tx.method, body: tx.body });
    }
  },
});
```

---

## PWA + App Shell Architecture

### App Shell Pattern

The app shell is the minimal HTML, CSS, and JS needed to render the UI chrome (header, sidebar, navigation) without any data. It is precached by Serwist and loads instantly, even offline.

```
┌──────────────────────────────────────┐
│  App Shell (precached by Serwist)    │
│  ├── Header / Navigation             │
│  ├── Sidebar (if applicable)         │
│  ├── Main content area (skeleton)    │
│  └── Footer                          │
├──────────────────────────────────────┤
│  Dynamic Content (loaded by data)    │
│  ├── TanStack Query → Server data    │
│  ├── TanStack DB → Reactive local    │
│  └── Zustand → Ephemeral UI state    │
└──────────────────────────────────────┘
```

### Service Worker Caching Layers

| Layer | Strategy | What It Caches |
|---|---|---|
| Precache | Build-time manifest | App shell HTML, JS bundles, CSS, icons, fonts |
| Runtime — Pages | NetworkFirst | HTML documents (falls back to cached version offline) |
| Runtime — API | NetworkFirst or StaleWhileRevalidate | API responses for read-only data |
| Runtime — Static | CacheFirst | Images, media, third-party assets |
| Runtime — RSC | StaleWhileRevalidate | React Server Component payloads |
| Fallback | Offline page | `/~offline` page when network and cache both fail |

**Rule:** Never cache user-specific API responses in the service worker. Use TanStack DB / PouchDB for structured data caching. The service worker caches the shell and generic static assets only.

### Serwist Configuration Architecture

```ts
// app/sw.ts — production service worker
import { defaultCache } from "@serwist/next/worker";
import { Serwist, CacheFirst, NetworkFirst, StaleWhileRevalidate } from "serwist";

const serwist = new Serwist({
  precacheEntries: self.__SW_MANIFEST,
  skipWaiting: true,
  clientsClaim: true,
  navigationPreload: true,
  runtimeCaching: [
    ...defaultCache, // Next.js-specific RSC caching
    {
      matcher: ({ url }) => url.pathname.startsWith("/api/public/"),
      handler: new StaleWhileRevalidate({
        cacheName: "public-api",
        plugins: [{ cacheWillUpdate: async ({ response }) => response?.ok ? response : null }],
      }),
    },
    {
      matcher: ({ request }) => request.destination === "image",
      handler: new CacheFirst({
        cacheName: "images",
        plugins: [
          { cacheableResponse: { statuses: [0, 200] } },
          { expiration: { maxEntries: 200, maxAgeSeconds: 30 * 24 * 60 * 60 } },
        ],
      }),
    },
  ],
  fallbacks: {
    entries: [
      { url: "/~offline", matcher: ({ request }) => request.destination === "document" },
    ],
  },
});

serwist.addEventListeners();
```

### Background Sync for Mutations

When a POST/PUT/DELETE fails due to network loss, queue it for background sync:

```ts
import { BackgroundSyncQueue, NetworkOnly } from "serwist";

const mutationQueue = new BackgroundSyncQueue("mutations", {
  maxRetentionTime: 24 * 60, // retry for up to 24 hours
  onSync: async ({ queue }) => {
    let entry;
    while ((entry = await queue.shiftRequest())) {
      try {
        await fetch(entry.request.clone());
      } catch (err) {
        await queue.unshiftRequest(entry);
        throw err;
      }
    }
  },
});
```

### Offline Fallback Strategy

1. **Precache the offline page** in `next.config.ts`:
```ts
const withSerwist = withSerwistInit({
  swSrc: "app/sw.ts",
  swDest: "public/sw.js",
  additionalPrecacheEntries: ["/~offline"],
});
```

2. **Create the offline page** at `app/~offline/page.tsx`:
```tsx
export default function OfflinePage() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center">
        <h1 className="text-2xl font-bold">You're offline</h1>
        <p className="mt-2 text-muted-foreground">
          Check your connection and try again.
        </p>
        <button
          onClick={() => window.location.reload()}
          className="mt-4 rounded-md bg-primary px-4 py-2 text-primary-foreground"
        >
          Retry
        </button>
      </div>
    </div>
  );
}
```

3. **Configure the fallback** in the Serwist constructor (see above).

---

## Next.js 16 Caching Architecture

### Cache Decision Tree

```
Is this data user-specific?
├── Yes → Can it use cookies for identification?
│   ├── Yes → "use cache: private" (browser cache, reads cookies)
│   └── No → No cache (dynamic rendering)
└── No → Is it shared across all users?
    ├── Yes → Is it acceptable to serve stale for seconds/minutes?
    │   ├── Yes → "use cache" with cacheLife("minutes"/"hours")
    │   └── No → "use cache" with cacheLife("seconds") + revalidateTag
    └── Semi-shared (per-locale, per-region)?
        → "use cache: remote" with cacheTag per segment
```

### Cache Invalidation Patterns

```ts
// Server Action performs mutation → invalidates cache
"use server";
import { revalidateTag, updateTag } from "next/cache";

export async function updateProduct(id: string, data: UpdateProductInput) {
  await db.products.update(id, data);

  // Invalidate all caches tagged "products"
  revalidateTag("products");

  // Read-your-writes: instantly reflect in current user's next render
  updateTag(`product-${id}`);
}
```

---

## Rendering Strategy Decision Framework

| Scenario | Strategy | Why |
|---|---|---|
| Marketing page, blog post | SSG via `"use cache"` + `cacheLife("days")` | Content rarely changes, maximize CDN caching |
| Product listing (shared) | ISR via `"use cache"` + `cacheLife("hours")` + `revalidateTag` | Updated periodically, invalidated on write |
| User dashboard | Dynamic RSC (no cache) | User-specific, always fresh |
| User preferences | `"use cache: private"` | Browser-only cache, reads cookies |
| Search results page | SSR with streaming | Personalized but benefits from streaming partial content |
| Interactive feature (cart, forms) | Client Component + TanStack Query/DB | Needs local state, real-time reactivity |
| Offline-capable feature | Client Component + TanStack DB + Serwist | Must work without network |

### Server Component → Client Component Data Flow

```tsx
// page.tsx (Server Component)
export default async function ProductPage({ params }: Props) {
  const { id } = await params;
  const queryClient = getQueryClient();
  await queryClient.prefetchQuery(productDetailOptions(id));

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      {/* Server-rendered product info */}
      <ProductHeader id={id} />
      {/* Client-rendered interactive section */}
      <ProductActions id={id} />
    </HydrationBoundary>
  );
}
```

The Server Component fetches and prefetches. The Client Component picks up the hydrated cache and adds interactivity. TanStack DB collections provide reactive local queries on top of the same data.