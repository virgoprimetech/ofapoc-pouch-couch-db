# Authentication & Authorization

This document describes how users authenticate and how access is controlled across the OFA system, from the browser request through `ofa-web`, the gateway, and the identity service.

## Audience

Developers who work on or debug the OFA auth flow.

## Architecture overview

```text
Browser                        ofa-web (Next.js)             ofa-gw (Spring              identity (Spring
                               proxy.ts + Server Actions     Cloud Gateway)              Boot)
──────┐                        ┌─────────────────────┐       ┌──────────────────┐        ┌─────────────────┐
      │  POST /auth/login      │  loginAction()       │       │  /auth/**        │        │  LoginHandler    │
      │  {tenant, username,    │  Zod validates input │──────▶│  → identity      │───────▶│  Chain of         │
      │   password}            │  Sets httpOnly       │       │                  │        │  Responsibility   │
      │                        │  cookies             │       │  /db/**          │        │  processors       │
      │  Cookie:               │  Returns user +      │◀──────│  → CouchDB       │◀───────│                  │
      │  ofa_access_token      │  accessToken         │       │                  │        │  JWT tokens       │
      │  ofa_refresh_token     │                      │       │  X-User-Id header │        │  returned         │
      │                        │                      │       │  (placeholder)    │        │                  │
      └────────────────────────┘                       └───────┘──────────────────┘        └─────────────────┘
```

## Authentication flow

### 1. Login

The login flow uses a Server Action. The browser does not send credentials to a client-side API route, and the app does not persist tokens in browser storage.

1. The user submits the login form at `/login`.
2. `useActionState` calls `loginAction` with `{ tenant, username, password }`.
3. `loginAction` validates the input with `LoginSchema`. If validation fails, it returns a typed error object and does not call the backend.
4. `loginAction` sends `POST /auth/login` to the identity service through the gateway (`OFA_GW_URL`, default `http://localhost:8080`).
5. The identity service executes a Chain of Responsibility:

   | Order | Processor | Schema | Transactional? | Purpose |
   |-------|-----------|--------|---------------|---------|
   | 10 | `TenantValidationProcessor` | Public | No | Finds the tenant by code, checks that it is active, and sets `TenantContext` |
   | 20 | `UserAuthenticationProcessor` | Tenant | Yes (read-only) | Finds the user by username, checks `ACTIVE` status, and verifies the BCrypt password |
   | 30 | `RoleResolutionProcessor` | Tenant | Yes (read-only) | Loads valid role assignments and resolves role codes and property IDs |
   | 40 | `TokenGenerationProcessor` | — | No | Generates RSA-signed access and refresh JWTs |

6. The identity service returns a `LoginResponse`:

   ```json
   {
     "accessToken": "eyJhbG...",
     "refreshToken": "eyJhbG...",
     "tokenType": "Bearer",
     "expiresIn": 900,
     "tenantId": "76da8ebd-9348-4c8c-b15b-07f100c8fd73",
     "userId": "019dd715-d870-7958-8d20-62ce88a8c9c0",
     "username": "virgodarth",
     "email": "virgo@primetech.com.vn",
     "roles": ["TENANT_ADMIN"]
   }
   ```

7. `loginAction` validates the response with `LoginResponseSchema` and sets two `httpOnly` cookies:

   | Cookie | Purpose | `maxAge` | Flags |
   |--------|---------|----------|-------|
   | `ofa_access_token` | JWT access token | Matches `expiresIn` from identity | `httpOnly`, `secure` (prod), `sameSite=Lax`, `path=/` |
   | `ofa_refresh_token` | JWT refresh token | 7 days | `httpOnly`, `secure` (prod), `sameSite=Lax`, `path=/` |

8. `loginAction` returns `{ success: true, user, accessToken }` to the client.
9. The client stores `user` in the persisted Zustand auth store, keeps `accessToken` in memory only, and navigates to `/`.
10. On reload, `SyncProvider` rehydrates the in-memory access token from the `ofa_access_token` cookie by calling `getAccessTokenAction()`.

### 2. Route protection

Route protection happens at two layers.

**Layer 1: `proxy.ts`**

Next.js 16 uses `proxy.ts` instead of `middleware.ts`. The proxy runs on every request and checks the `ofa_access_token` cookie.

- No cookie + protected path → redirect to `/login`
- Cookie exists + `/login` → redirect to `/`
- Cookie exists + protected path → allow
- Static assets (`/_next`, `/sw`, images, fonts) → allow

```text
proxy.ts decision tree:
  Is the path a static asset or internal path?  → allow
  Is the path /login AND cookie exists?         → redirect to /
  Is the path NOT /login AND no cookie?         → redirect to /login
  Otherwise                                     → allow
```

> **Note:** `proxy.ts` checks only for cookie presence. It does not validate JWT expiry and it does not perform refresh itself.

**Layer 2: API gateway (`ofa-gw`)**

The current gateway `AuthenticationFilter` injects a random `X-User-Id` header on every request. This is still a placeholder. The intended future behavior is to validate the JWT from the `Authorization` header or cookie and inject the real user ID.

**Layer 3: Identity service (`JwtAuthenticationFilter`)**

For direct identity service API calls that do not go through the gateway:

1. The filter extracts the `Bearer` token from the `Authorization` header.
2. It validates the JWT signature using RSA public keys from `/.well-known/jwks.json`.
3. It extracts `tenant_code`, `userId`, and `roles` from the claims.
4. It sets `TenantContext` and `SecurityContextHolder`.
5. It clears `TenantContext` in a `finally` block.

### 3. Token refresh

When a sync-related authenticated request fails with `401` or `403`, `ofa-web` refreshes the token pair automatically.

1. The client clears the stale in-memory access token.
2. The client calls `refreshAccessTokenAction()`.
3. `refreshAccessTokenAction()` reads `ofa_refresh_token` from the `httpOnly` cookie.
4. The action sends `POST /auth/refresh` with the refresh token in the JSON body:

   ```json
   {
     "refreshToken": "eyJhbG..."
   }
   ```

5. The identity service `RefreshHandler` validates the refresh token, checks `type=refresh`, re-validates the tenant and user, resolves current roles, and issues a new token pair.
6. On success, `ofa-web` rotates both cookies, restores the new access token in memory, and retries the failed request once.
7. If refresh fails with a definitive auth error such as `401` or `403`, the client clears both cookies, clears the auth store, stops sync, and returns the user to `/login`.
8. If refresh fails because of a transient network or service problem, the sync layer falls back to its existing retry and offline behavior.

`ofa-web` prevents refresh storms. If several requests fail with auth errors at the same time, they share the same in-flight refresh request.

> **Note:** The current implementation refreshes reactively after an auth failure. It does not refresh proactively before expiry.

> **Note:** The current refresh-and-retry path is implemented for sync and CouchDB-facing requests. There is not yet a shared authenticated fetch wrapper for other future browser API calls.

> **Note:** The refresh response returns `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `tenantId`, `userId`, `email`, and `roles`. It does not return `username`.

> **Note:** The client keeps `username` in persisted `AuthUser` metadata from login and continues to use that value after refresh.

> **Warning:** The current implementation deletes the access-token cookie before it attempts refresh. If refresh fails because of a temporary outage, route protection can temporarily treat the session as signed out until a later successful login or refresh restores the cookie.

> **Warning:** Full browser verification of the automatic retry path with an intentionally expired or invalid access token still needs a manual end-to-end session test.

> **Note:** Live verification with account `ofapoc_001` / `virgodarth` confirmed that both `/auth/login` and `/auth/refresh` return `200 OK` with the documented payload shapes.

### 4. Logout

1. The client calls `logoutAction()`.
2. The Server Action deletes `ofa_access_token` and `ofa_refresh_token`.
3. The Server Action redirects to `/login`.

The identity service `POST /auth/logout` endpoint returns `204 No Content` but is currently a no-op because JWT auth is stateless. Token blacklisting is not implemented.

### 5. Register

```text
POST /auth/register
{ "username", "email", "password", "firstName?", "lastName?" }
```

This endpoint returns `201 Created` with a `RegisterResponse`. Registration does not log the user in automatically. The user must call `/auth/login` after registering.

## JWT claims

The access token contains these claims:

| Claim | Type | Source |
|-------|------|--------|
| `iss` | String | `jwtProperties.issuer` |
| `sub` | UUID | `user.id()` |
| `aud` | String | `jwtProperties.audience` |
| `iat` | Instant | Token creation time |
| `exp` | Instant | `iat` + `accessTokenExpirationMs` |
| `tenant_id` | UUID | `tenant.id` |
| `tenant_code` | String | `tenant.code` |
| `username` | String | `user.username().value()` |
| `email` | String | `user.email().value()` |
| `roles` | String[] | Role codes from assignments |
| `propertyIds` | String[] | Property IDs from property-scoped assignments |

The refresh token contains a subset:

| Claim | Type |
|-------|------|
| `iss`, `sub`, `aud`, `iat`, `exp` | Standard |
| `type` | `"refresh"` |
| `tenant_id` | UUID |
| `tenant_code` | String |

## Multi-tenancy

OFA uses schema-per-tenant isolation.

1. The login request includes `tenant` such as `ofapoc_001`.
2. `TenantValidationProcessor` finds the tenant in the public schema and loads `schemaName`.
3. `TenantContext.setTenant(code, id, schemaName)` is set before transactional processors run.
4. Hibernate's `SchemaMultiTenantConnectionProvider` routes later queries to the tenant schema.
5. `TenantContext.clear()` runs in a `finally` block.

## Frontend auth architecture

### Files

| File | Layer | Responsibility |
|------|-------|---------------|
| `ofa-web/src/features/auth/schemas.ts` | Shared | Zod schemas for login, refresh, and client-visible auth types |
| `ofa-web/src/features/auth/store.ts` | Client | Zustand store for persisted `AuthUser` metadata and in-memory access token |
| `ofa-web/src/features/auth/actions.ts` | Server | Server Actions for login, refresh, logout, and cookie-backed access-token rehydration |
| `ofa-web/src/features/auth/refresh-client.ts` | Client | Single-flight refresh coordinator |
| `ofa-web/src/components/providers/sync-provider.tsx` | Client | Rehydrates access token, starts sync, and clears auth state on terminal sync auth failure |
| `ofa-web/src/lib/db/sync.ts` | Client | Sync runtime, auth-failure detection, one-time refresh retry, and auth-failure event emission |
| `ofa-web/src/app/(auth)/login/login-form.tsx` | Client | Login form component built with `useActionState` |
| `ofa-web/src/app/(auth)/layout.tsx` | Server | Auth-only centered layout |
| `ofa-web/proxy.ts` | Runtime | Cookie-based route gate |

### Data flow

```text
Browser
  │
  ├─ LoginForm (useActionState)
  │    │
  │    └─ loginAction()
  │         ├─ Zod validates login input
  │         ├─ POST /auth/login → gateway → identity
  │         ├─ Zod validates login response
  │         ├─ Sets httpOnly access + refresh cookies
  │         └─ Returns { success, user, accessToken }
  │
  ├─ useAuthStore.setUser(user, accessToken)
  │
  ├─ Reload
  │    └─ SyncProvider → getAccessTokenAction() → rehydrate in-memory access token
  │
  └─ Authenticated sync request fails with 401/403
       ├─ refreshAccessTokenClient()
       ├─ refreshAccessTokenAction()
       ├─ POST /auth/refresh
       ├─ rotate both cookies
       ├─ restore in-memory access token
       └─ retry failed request once
```

## Security decisions

| Decision | Rationale |
|----------|-----------|
| Store tokens in `httpOnly` cookies | JavaScript cannot read them, which reduces XSS token theft risk |
| Keep the access token in memory only | The app avoids persisting tokens in browser storage |
| Persist only `AuthUser` metadata | The app retains display state without persisting credentials |
| Use `sameSite=Lax` cookies | Reduces CSRF risk while still allowing top-level navigation |
| Set `secure` in production | Prevents token transport over plain HTTP |
| Validate Server Action input with Zod | The client is untrusted |
| Validate identity responses with Zod | Catches malformed backend responses early |
| Retry once after refresh | Recovers from expiry without creating endless retry loops |
| Use a single in-flight refresh promise | Prevents concurrent refresh storms |
| Keep refresh logic out of `proxy.ts` | Separates route gating from runtime request recovery |

## What happens when the network is offline

OFA is an offline-first PWA.

- Login is not possible because the Server Action must reach the identity service.
- Already-authenticated sessions can continue to navigate as long as route protection still sees the access-token cookie.
- PouchDB sync pauses and reports `offline` or `retrying`.
- Transient refresh failures fall back to existing sync retry behavior instead of forcing an immediate hard logout.

## API reference

### `POST /auth/login`

Authenticate a user and receive JWT tokens.

**Request body:**

```json
{
  "tenant": "ofapoc_001",
  "username": "virgodarth",
  "password": "virgodarth"
}
```

| Field | Type | Constraints |
|-------|------|-------------|
| `tenant` | string | Required. 2–50 chars. Must start with a lowercase letter. Only lowercase letters, numbers, underscores, and hyphens. |
| `username` | string | Required. 3–50 chars. Only letters, numbers, underscores, and hyphens. |
| `password` | string | Required. |

**Response `200 OK`:**

```json
{
  "accessToken": "<access_token>",
  "refreshToken": "<refresh_token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "tenantId": "76da8ebd-9348-4c8c-b15b-07f100c8fd73",
  "userId": "019dd715-d870-7958-8d20-62ce88a8c9c0",
  "username": "virgodarth",
  "email": "virgo@primetech.com.vn",
  "roles": ["TENANT_ADMIN"]
}
```

### `POST /auth/refresh`

Refresh an expired or invalid access token by using a valid refresh token.

**Request body:**

```json
{
  "refreshToken": "<refresh_token>"
}
```

**Response `200 OK`:**

```json
{
  "accessToken": "<new_access_token>",
  "refreshToken": "<new_refresh_token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "tenantId": "76da8ebd-9348-4c8c-b15b-07f100c8fd73",
  "userId": "019dd715-d870-7958-8d20-62ce88a8c9c0",
  "email": "virgo@primetech.com.vn",
  "roles": ["TENANT_ADMIN"]
}
```

> **Note:** The backend refresh handler uses the posted refresh token as the source of truth.

### `POST /auth/logout`

End the current session locally.

**Response:** `204 No Content`

### `POST /auth/register`

Create a new user account.

Registration does not log the user in automatically.

## Environment variables

| Variable | Where | Purpose |
|----------|-------|---------|
| `OFA_GW_URL` | `ofa-web` server environment | Gateway URL used by Server Actions |
| `NEXT_PUBLIC_COUCHDB_URL` | `ofa-web` client environment | CouchDB URL used by sync |

## Known gaps

1. `proxy.ts` still checks cookie presence only. It does not validate expiry.
2. Browser-level end-to-end verification of the automatic retry path still needs a manual session test with an intentionally invalid or expired access token.
3. The gateway auth filter is still a placeholder.
4. Token revocation and blacklisting are still not implemented.
5. The refresh-and-retry behavior currently targets sync-related authenticated requests, not every possible future browser API call.