# OFA POC — Offline-First Property Management System

Proof of concept for an offline-first PMS (Property Management System) using PouchDB and CouchDB for real-time data synchronization.

## Architecture

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

**Data flow**: PostgreSQL is the source of truth. An ETL process handles PostgreSQL to CouchDB data transfer. PouchDB synchronizes with CouchDB bidirectionally. The web app reads and writes to PouchDB locally, then syncs when connectivity is available.

## Tech stack

| Layer | Technology |
|-------|-----------|
| Frontend | Next.js 16.2, React 19, TypeScript 5, Tailwind CSS 4, shadcn/ui |
| Local database | PouchDB 9 (IndexedDB) |
| Sync relay | CouchDB 3.x |
| Backend API | Spring Boot 4, PostgreSQL 18 |
| Icons | Remix Icon |
| Validation | Zod |
| Infrastructure | Docker Compose |

## Quick start

### Prerequisites

- Node.js 20+ and pnpm
- Docker and Docker Compose
- Git

### 1. Start infrastructure

```bash
docker compose up -d
```

This starts:
- PostgreSQL on port `5432` (user: `postgres`, password: `postgres`, database: `ofa`)
- CouchDB on port `5984` (user: `admin`, password: `admin`)
- CouchDB init container (configures single-node cluster and CORS)

Verify CouchDB is running:

```bash
curl http://admin:admin@localhost:5984/_up
```

### 2. Start the frontend

```bash
cd ofa-web
pnpm install
pnpm dev
```

Open http://localhost:3000.

### 3. Create the CouchDB database

The first sync attempt creates the `properties` database in CouchDB automatically. Alternatively:

```bash
curl -X PUT http://admin:admin@localhost:5984/properties
```

## Environment variables

Copy `.env.example` to `.env.local` (frontend) or `.env` (root):

```bash
# PostgreSQL
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
POSTGRES_DB=ofa
POSTGRES_PORT=5432

# CouchDB
COUCHDB_USER=admin
COUCHDB_PASSWORD=admin
COUCHDB_PORT=5984

# Next.js — PouchDB to CouchDB sync URL
NEXT_PUBLIC_COUCHDB_URL=http://admin:admin@localhost:5984
```

## Project structure

```
├── compose.yaml              # Docker Compose (PostgreSQL + CouchDB)
├── scripts/
│   └── init-couchdb.sh       # CouchDB one-time setup (CORS, single-node)
├── .env.example              # Environment variables template
├── ofa-api/                  # Spring Boot 4 backend (Java)
│   └── src/
└── ofa-web/                  # Next.js 16 frontend (TypeScript)
    └── src/
        ├── app/              # Next.js App Router pages
        │   ├── properties/[id]/  # Property detail route
        │   └── (properties)/     # Property components
        ├── components/       # UI components (shadcn/ui + custom)
        │   ├── layout/       # App shell, sidebar, sync status
        │   ├── properties/   # Property CRUD components
        │   └── ui/           # shadcn/ui primitives
        ├── hooks/            # React hooks (use-toast, use-mobile)
        └── lib/
            ├── constants.ts  # Shared constants (status styles, type labels)
            └── db/           # PouchDB layer
                ├── index.ts               # PouchDB instance
                ├── schema.ts             # Zod schemas + TypeScript types
                ├── property-repository.ts # CRUD operations
                ├── sync.ts               # CouchDB sync + conflict resolution
                ├── use-properties.ts      # List hook (live changes feed)
                └── use-property.ts        # Single entity hook
```

## Offline-first behavior

| Scenario | What happens |
|----------|-------------|
| **Normal operation** | PouchDB reads/writes IndexedDB locally. Sync runs in background. |
| **Network drops** | All CRUD operations continue against local IndexedDB. Status indicator shows "Offline". |
| **Network returns** | Sync resumes automatically. Changes upload to CouchDB. |
| **Conflict** | Last-write-wins by `updated_at`. PostgreSQL ETL is the final authority. |
| **Edit while offline** | Writes to local IndexedDB. Toast shows "Saved locally". Syncs when online. |

## Property entity

The Property document includes:

| Field group | Fields |
|-------------|--------|
| Core | name, property_type (11 types), status (5 states), description |
| Address | address_line1, address_line2, city, state_province, postal_code, country |
| Geolocation | latitude, longitude |
| Contact | phone, email, website |
| Operational | timezone (IANA), currency (ISO 4217), total_rooms |
| System | tenant_id, created_at, updated_at, deleted_at |

**Status lifecycle**: DRAFT → ACTIVE → SUSPENDED → CLOSED → ARCHIVED

Documents use `_id` prefix `property::uuid` and `type: "property"` discriminator. Deletion is soft-delete only (sets `deleted_at` timestamp).

## Useful URLs

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| CouchDB Fauxton UI | http://localhost:5984/_utils |
| PostgreSQL | localhost:5432 |
