# Architecture

System architecture for the OFA offline-first Property Management System.

## Audience

Developers working on `ofa-web`, `ofa-gw`, `identity`, or `ofa-api`. Read this document to understand the offline-first client model, auth boundary, sync path, and backend responsibilities.

## What this system is

OFA is an offline-first PMS proof of concept.

The browser is the primary working surface. The app reads and writes local data through PouchDB first. PouchDB stores documents in IndexedDB and replicates with CouchDB when connectivity is available. Backend services treat PostgreSQL as the source of truth and synchronize data between PostgreSQL and CouchDB.

## Core principles

- The browser reads and writes local data first.
- IndexedDB is the browser persistence layer.
- CouchDB is the sync relay and shared document store.
- PostgreSQL is the source of truth.
- `ofa-api` synchronizes data between CouchDB and PostgreSQL.
- `ofa-gw` fronts identity and CouchDB endpoints for the web app.
- `identity` authenticates users, resolves tenant context, and provisions CouchDB access.

## System context (C4 level 1)

```mermaid
flowchart LR
    user[PMS user]
    ofa[OFA offline-first PMS]
    couch[(CouchDB)]
    pg[(PostgreSQL)]
    idp[External IdP<br/>future / optional]

    user --> ofa
    ofa --> couch
    ofa --> pg
    ofa -. optional federation .-> idp
```

## Container view (C4 level 2)

```mermaid
flowchart LR
    subgraph Browser
        web[ofa-web<br/>Next.js 16 + React 19<br/>UI + Server Actions + PouchDB client]
    end

    gw[ofa-gw<br/>Spring Cloud Gateway]
    identity[identity<br/>Spring Boot auth + tenant provisioning]
    couch[(CouchDB<br/>global + scoped DBs)]
    api[ofa-api<br/>Spring Boot ETL / reverse sync]
    pg[(PostgreSQL<br/>source of truth)]

    web -->|/identity/**| gw
    web -->|/db/** sync traffic| gw
    gw -->|auth routes| identity
    gw -->|strip /db + forward| couch
    identity -->|provision users / security| couch
    api -->|read/write docs| couch
    api -->|read/write rows| pg
```

## Component view: browser runtime (C4 level 3)

```mermaid
flowchart TD
    subgraph ofa-web
        ui[App Router pages + UI components]
        actions[Server Actions<br/>src/features/auth/actions.ts]
        proxy[proxy.ts<br/>cookie-based route guard]
        shell[Client shell + Sync provider]
        repo[Repository + hooks]
        sync[sync.ts]
        conflict[conflict.ts]
        pouch[PouchDB]
        idb[(IndexedDB)]
    end

    ui --> repo
    ui --> actions
    proxy --> ui
    shell --> sync
    repo --> pouch
    sync --> pouch
    conflict --> pouch
    pouch --> idb
```

## Main runtime topology

```mermaid
flowchart LR
    subgraph Browser
        ui[Next.js UI]
        repo[Repository / hooks]
        pouch[PouchDB]
        idb[(IndexedDB)]

        ui --> repo
        repo --> pouch
        pouch <--> idb
    end

    gw[ofa-gw]
    couch[(CouchDB)]
    api[ofa-api sync services]
    pg[(PostgreSQL)]

    pouch <-->|live replication| gw
    gw --> couch
    couch <-->|bootstrap + changes| api
    api --> pg
    pg --> api
```

## Authentication and access flow

The app separates sign-in from sync data access.

### Auth path

1. The user submits credentials in `ofa-web`.
2. `ofa-web` calls Server Actions in `ofa-web/src/features/auth/actions.ts`.
3. Server Actions call `ofa-gw` on `/identity/**`.
4. `ofa-gw` forwards to `identity`.
5. `identity` runs the login processor chain:
   1. `TenantValidationProcessor`
   2. `UserAuthenticationProcessor`
   3. `RoleResolutionProcessor`
   4. `TokenGenerationProcessor`
6. `identity` returns access and refresh tokens.
7. `ofa-web` stores tokens in httpOnly cookies: `ofa_access_token`, `ofa_refresh_token`.
8. `ofa-web/proxy.ts` protects routes by cookie presence.
9. `ofa-gw` validates JWTs against the identity JWKS endpoint.

### CouchDB access path

1. Browser sync targets `/db/**` through `ofa-gw`.
2. `ofa-gw` strips `/db` before forwarding to CouchDB.
3. Gateway forwarding removes the browser `Cookie` header.
4. `CouchedbAuthorizationFilterFactory` derives upstream CouchDB auth headers from the authenticated JWT context.
5. CouchDB accepts or rejects the request based on the effective database access.

> **Note:** Current gateway auth injection is still implementation-in-progress. `ofa-gw/src/main/java/vn/com/primetech/ofagw/security/CouchedbAuthorizationFilterFactory.java` currently sets `X-Auth-CouchDB-*` headers with placeholder admin-oriented values and an HMAC token, while identity already exposes RSA/JWKS-based JWT verification for OFA auth.

## Offline-first data flow

### Read path

```mermaid
sequenceDiagram
    participant UI as UI
    participant Repo as Repository / hook
    participant Pouch as PouchDB
    participant IDB as IndexedDB

    UI->>Repo: request data
    Repo->>Pouch: query document(s)
    Pouch->>IDB: read local state
    IDB-->>Pouch: result
    Pouch-->>Repo: document(s)
    Repo-->>UI: render response
```

Reads do not depend on network availability if the required documents already exist locally.

### Write path

```mermaid
sequenceDiagram
    participant User as User action
    participant Repo as Repository
    participant Pouch as PouchDB
    participant IDB as IndexedDB
    participant GW as ofa-gw
    participant Couch as CouchDB
    participant API as ofa-api
    participant PG as PostgreSQL

    User->>Repo: create / update / delete
    Repo->>Pouch: validate + write doc
    Pouch->>IDB: persist locally
    IDB-->>Pouch: ack
    Pouch-->>Repo: success
    Repo-->>User: immediate UI update
    Pouch->>GW: replicate when online
    GW->>Couch: forward request
    Couch->>API: changes available
    API->>PG: import final doc state
```

Local success is the first success condition. Server synchronization follows the local write.

### Bootstrap path

```mermaid
sequenceDiagram
    participant PG as PostgreSQL
    participant API as ofa-api
    participant Couch as CouchDB
    participant GW as ofa-gw
    participant Pouch as PouchDB
    participant IDB as IndexedDB
    participant UI as UI

    PG->>API: source data
    API->>Couch: bootstrap / reverse sync
    Pouch->>GW: pull replication
    GW->>Couch: forward request
    Couch-->>Pouch: documents
    Pouch->>IDB: store locally
    Pouch-->>UI: reactive updates
```

This path seeds the browser with server-side state when local data is empty or stale.

### Conflict path

```mermaid
sequenceDiagram
    participant C1 as Client A
    participant C2 as Client B
    participant Couch as CouchDB
    participant Resolve as conflict.ts / sync.ts
    participant API as ofa-api
    participant PG as PostgreSQL

    C1->>Couch: sync offline edit later
    C2->>Couch: sync competing edit later
    Couch-->>Resolve: conflicting revisions
    Resolve->>Couch: auto-resolve or manual merge
    Couch->>API: resolved document
    API->>PG: import converged state
```

PostgreSQL remains the final authority even though conflict handling starts in the client/CouchDB layer.

## End-to-end data flow

### Property created in browser

```text
Browser UI
  → `property-repository.ts`
  → PouchDB local write
  → IndexedDB durable local state
  → `sync.ts` replication via `ofa-gw`
  → CouchDB document
  → `ofa-api` SyncService / PropertySyncService
  → PostgreSQL row
```

### Server-side data reaches browser

```text
PostgreSQL change
  → `ofa-api` ReverseSyncService
  → CouchDB document update
  → browser live sync pull via `ofa-gw`
  → PouchDB update
  → React hooks changes feed
  → UI rerender
```

### Login plus tenant DB provisioning

```text
Login request
  → `ofa-gw`
  → `identity` auth chain
  → tenant context resolution
  → CouchDB user / security provisioning
  → JWT issuance
  → httpOnly cookies in `ofa-web`
  → subsequent `/db` access via gateway
```

## Runtime behavior in `ofa-web`

`ofa-web/src/lib/db/sync.ts` drives sync from the browser.

- It builds sync targets from `NEXT_PUBLIC_COUCHDB_GLOBAL_DB` and tenant-scoped DB names derived from JWT claims.
- It checks `/db/_up` through the gateway before opening replication.
- It sends bearer tokens on sync requests.
- It retries failed sync connections with exponential backoff and jitter.
- It attempts token refresh after `401` or `403` responses.
- It tracks aggregate sync state across all configured targets: `idle`, `syncing`, `synced`, `error`, `offline`, `retrying`.

## Offline behavior

| Scenario | Result |
|---|---|
| Browser online, CouchDB reachable | Local read/write plus background replication |
| Browser offline | Local read/write continues against IndexedDB |
| Browser reconnects | Sync health check runs, then replication restarts |
| Access token expires during sync | Client refreshes token, then retries authenticated sync |
| Same document edited in multiple clients | CouchDB stores conflict branches, then client/server converge |
| Hard refresh while offline | Local DB persists, but app shell still depends on uncached web assets |

> **Note:** This POC does not currently rely on a service worker for full app-shell offline delivery. The offline-first guarantee applies to local data behavior, not complete static asset caching.

## Service responsibilities

| Service | Responsibility |
|---|---|
| `ofa-web` | UI, auth actions, local-first reads/writes, sync lifecycle, conflict UX |
| `ofa-gw` | Web ingress, `/identity/**` and `/db/**` routing, JWT validation, upstream CouchDB header mediation |
| `identity` | Login, refresh, JWT issuance, JWKS exposure, tenant context, CouchDB user/security provisioning |
| `ofa-api` | ETL import from CouchDB, reverse sync from PostgreSQL, checkpointing |
| CouchDB | Shared sync store, replication endpoint, conflict retention |
| PostgreSQL | Source of truth for business data |

## Key code locations

| Area | Files |
|---|---|
| Browser auth | `ofa-web/src/features/auth/actions.ts`, `ofa-web/proxy.ts` |
| Browser sync | `ofa-web/src/lib/db/sync.ts`, `ofa-web/src/components/providers/sync-provider.tsx` |
| Browser conflicts | `ofa-web/src/lib/db/conflict.ts` |
| Browser DB model | `ofa-web/src/lib/db/index.ts`, `ofa-web/src/lib/db/schema.ts` |
| Gateway CouchDB proxy | `ofa-gw/src/main/resources/application.yaml`, `ofa-gw/src/main/java/vn/com/primetech/ofagw/security/CouchedbAuthorizationFilterFactory.java` |
| Identity auth | `identity/src/main/java/com/primetech/poc/identity/features/auth/` |
| Identity RSA / JWKS | `identity/src/main/java/com/primetech/poc/identity/shared/jose/` |
| Identity multi-tenancy | `identity/src/main/java/com/primetech/poc/identity/shared/multitenancy/` |
| Identity CouchDB integration | `identity/src/main/java/com/primetech/poc/identity/shared/couchdb/` |
| API sync | `ofa-api/src/main/java/com/primetech/poc/ofaapi/features/sync/application/` |

## Deployment shape for local development

```text
Developer machine
├── Browser
├── ofa-web dev server
├── ofa-gw
├── identity
├── ofa-api
├── CouchDB container
└── PostgreSQL container
```

`compose.yaml` starts PostgreSQL, CouchDB, and the CouchDB init container. Spring services and the Next.js app run separately during development.

## Related documents

- [README.md](../README.md)
- [pouchdb-couchdb-sync.md](./pouchdb-couchdb-sync.md)
- [etl-sync.md](./etl-sync.md)
- [architecture/authentication.md](./architecture/authentication.md)
- [couchdb docs](./couchdb/)
