# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo shape

Offline-first PMS POC with four apps:
- `ofa-web` — Next.js 16 / React 19 frontend; browser uses PouchDB + IndexedDB
- `ofa-gw` — Spring Cloud Gateway; proxies auth to `identity` and `/db/**` to CouchDB
- `identity` — Spring Boot identity service with schema-per-tenant auth and tenant provisioning
- `ofa-api` — Spring Boot backend; PostgreSQL source of truth plus CouchDB ETL sync
- `compose.yaml` — local PostgreSQL + CouchDB + CouchDB init container

## Common commands

### Infra
- Start local infra: `docker compose up -d`
- Stop infra: `docker compose down`
- CouchDB health: `curl http://admin:admin@localhost:5984/_up`
- CouchDB Fauxton: `http://localhost:5984/_utils`

### Frontend (`ofa-web`)
- Install deps: `cd ofa-web && pnpm install`
- Dev server: `cd ofa-web && pnpm dev`
- Production build: `cd ofa-web && pnpm build`
- Start built app: `cd ofa-web && pnpm start`
- Lint: `cd ofa-web && pnpm lint`
- Clean Next cache: `cd ofa-web && pnpm clean`

### Backend services
- Run `identity`: `cd identity && mvn spring-boot:run`
- Run `ofa-api`: `cd ofa-api && mvn spring-boot:run`
- Run `ofa-gw`: `cd ofa-gw && mvn spring-boot:run`
- Test a module: `mvn -f identity/pom.xml test` / `mvn -f ofa-api/pom.xml test` / `mvn -f ofa-gw/pom.xml test`
- Run one test class: `mvn -f identity/pom.xml -Dtest=IdentityApplicationTests test`
- Run one test method: `mvn -f identity/pom.xml -Dtest=IdentityApplicationTests#contextLoads test`
- Package a module: `mvn -f identity/pom.xml package`

## Important repo-specific guidance

### Next.js guidance
`ofa-web/AGENTS.md` says this explicitly: this repo uses a newer Next.js version with breaking changes. Before changing framework-level behavior, read the relevant docs under `ofa-web/node_modules/next/dist/docs/` instead of relying on old Next.js assumptions.

### Frontend auth flow
Read `docs/architecture/authentication.md` before changing login/refresh/protected-route behavior.

Current shape:
- `ofa-web` uses Server Actions in `src/features/auth/actions.ts` for login/refresh/logout
- auth cookies are httpOnly: `ofa_access_token`, `ofa_refresh_token`
- `proxy.ts` in `ofa-web` protects routes by cookie presence only
- `ofa-gw` routes `/identity/**` to the identity service
- identity login runs a Chain of Responsibility:
  - `TenantValidationProcessor`
  - `UserAuthenticationProcessor`
  - `RoleResolutionProcessor`
  - `TokenGenerationProcessor`
- identity direct API auth uses `JwtAuthenticationFilter`

Important nuance: refresh is reactive after `401/403`, not proactive. `ofa-web/src/features/auth/actions.ts` currently clears the access-token cookie before refresh.

### Multi-tenancy model
Identity uses schema-per-tenant isolation, not row-level tenancy.

Key classes:
- `identity/src/main/java/com/primetech/poc/identity/shared/multitenancy/TenantContext.java`
- `identity/src/main/java/com/primetech/poc/identity/shared/multitenancy/SchemaMultiTenantConnectionProvider.java`
- `identity/src/main/java/com/primetech/poc/identity/shared/multitenancy/TenantIdentifierResolver.java`

Auth/login sets tenant context from the public schema, then later JPA work is routed to the tenant schema.

### Offline-first architecture
The big-picture data flow is:
1. `ofa-web` reads/writes local PouchDB first
2. PouchDB syncs bidirectionally with CouchDB
3. `ofa-api` ETL sync imports CouchDB changes into PostgreSQL
4. PostgreSQL remains the source of truth

This is documented in:
- `README.md`
- `docs/pouchdb-couchdb-sync.md`
- `docs/etl-sync.md`

### PouchDB sync layer
If touching `ofa-web/src/lib/db/`, read `docs/pouchdb-couchdb-sync.md` first.

Important files:
- `ofa-web/src/lib/db/index.ts` — PouchDB instance + Mango index setup
- `ofa-web/src/lib/db/schema.ts` — Zod schemas and document typing
- `ofa-web/src/lib/db/sync.ts` — live sync lifecycle, retry/backoff, status state machine
- `ofa-web/src/lib/db/conflict.ts` — conflict detection and resolution
- `ofa-web/src/components/providers/sync-provider.tsx` — starts sync on mount
- `ofa-web/src/components/layout/client-shell.tsx` — SSR boundary for browser-only sync code

Important nuance: PouchDB code is intentionally kept out of SSR paths via dynamic import with `ssr: false`.

### ETL sync in `ofa-api`
If changing CouchDB/PostgreSQL sync behavior, read `docs/etl-sync.md` first.

Core pieces:
- `ofa-api/.../features/sync/application/SyncService.java` — bootstrap on startup + scheduled `_changes` polling
- `ofa-api/.../features/sync/application/PropertySyncService.java` — transforms CouchDB docs into PG rows
- `ofa-api/.../features/sync/application/ReverseSyncService.java` — pushes PG changes back to CouchDB
- `ofa-api/.../features/sync/domain/CouchDbCheckpoint.java` — stores last processed sequence
- `ofa-api/.../features/sync/infrastructure/CouchDbClient.java` — current HTTP client used by ETL

Important nuance: checkpointing is batch-level, not per-document. Bootstrap runs on `ApplicationReadyEvent` when no checkpoint exists.

### Gateway/CouchDB proxying
`ofa-gw` proxies CouchDB behind `/db/**`.

Key files:
- `ofa-gw/src/main/resources/application.yaml`
- `ofa-gw/src/main/java/vn/com/primetech/ofagw/security/CouchedbAuthorizationFilterFactory.java`

Important nuances:
- gateway route strips `/db` before forwarding to CouchDB
- current CouchDB auth header injection is still partly placeholder/hardcoded
- gateway removes `Cookie` by default before forwarding

### Identity service structure
Identity is organized by feature packages plus shared infrastructure.

Look at:
- `identity/src/main/java/com/primetech/poc/identity/features/auth/`
- `identity/src/main/java/com/primetech/poc/identity/features/tenant/`
- `identity/src/main/java/com/primetech/poc/identity/shared/`

Notable shared areas:
- `shared/security` — JWT creation/validation
- `shared/multitenancy` — tenant schema routing
- `shared/jose` — RSA/JWKS support
- `shared/couchdb` — CouchDB client/provisioning integration

### Identity CouchDB client conventions
When working in `identity/src/main/java/com/primetech/poc/identity/shared/couchdb/`:
- `CouchDbClient` is the identity-side OpenFeign client for the provisioning/security APIs documented in `docs/couchdb/*.md`
- DTOs are grouped by API under `shared/couchdb/dto/` subpackages, not kept in one flat package:
  - `dto/create_db/`
  - `dto/get_db/`
  - `dto/get_security/`
  - `dto/update_security/`
  - `dto/create_user/`
- shared security payload pieces live in top-level DTOs such as `dto/DatabaseAccess.java`
- current DB info response shape uses `get_db/DatabaseInfoResponse` with nested `DatabaseCluster`, `DatabaseSizes`, and `DatabaseProps`

## Current implementation caveats

- `identity` currently uses Spring Boot `4.1.0-M3`, while `ofa-api` and `ofa-gw` use Spring Boot `4.0.5`. Check compatibility before changing shared library assumptions.
- `compose.yaml` pins PostgreSQL but uses `couchdb:latest`; local behavior may drift over time.
- `ofa-web` already has its own local `CLAUDE.md`; preserve any more specific frontend guidance there.

## Useful docs to read before deeper changes

- `README.md` — overall architecture + local setup
- `docs/architecture/authentication.md` — end-to-end auth flow
- `docs/pouchdb-couchdb-sync.md` — browser sync lifecycle, conflicts, SSR boundaries
- `docs/etl-sync.md` — CouchDB ⇄ PostgreSQL ETL sync
- `docs/couchdb/*.md` — CouchDB provisioning/security API shapes used by identity
