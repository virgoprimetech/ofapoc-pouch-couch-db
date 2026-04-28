"use client";

import PouchDB from "pouchdb-browser";
import { refreshAccessTokenClient } from "@/features/auth/refresh-client";
import { parseTokenClaims } from "@/features/auth/token";
import { getDb } from "./index";
import type { PropertyDoc } from "./schema";

const COUCHDB_URL = process.env.NEXT_PUBLIC_COUCHDB_URL ?? "";
const COUCHDB_GLOBAL_DB = process.env.NEXT_PUBLIC_COUCHDB_GLOBAL_DB ?? "";
const COUCHDB_SCOPED_DBS = (process.env.NEXT_PUBLIC_COUCHDB_SCOPED_DBS ?? "")
  .split(",")
  .map((value) => value.trim())
  .filter(Boolean);
const HEALTH_CHECK_TIMEOUT_MS = 5_000;
const INITIAL_BACKOFF_MS = 1_000;
const MAX_BACKOFF_MS = 60_000;

interface SyncTarget {
  dbName: string;
  remoteUrl: string;
}

interface SyncSummary {
  total: number;
  active: number;
  paused: number;
  errored: number;
}

function buildSyncTargets(token: string | null): SyncTarget[] {
  const targets: SyncTarget[] = [];

  if (COUCHDB_GLOBAL_DB) {
    targets.push({
      dbName: COUCHDB_GLOBAL_DB,
      remoteUrl: `${COUCHDB_URL}/${COUCHDB_GLOBAL_DB}`,
    });
  }

  const claims = parseTokenClaims(token);
  if (!claims) {
    return targets;
  }

  for (const dbName of COUCHDB_SCOPED_DBS) {
    const scopedDbName = `${claims.tenant_code}$${claims.username}$${dbName}`;
    targets.push({
      dbName: scopedDbName,
      remoteUrl: `${COUCHDB_URL}/${scopedDbName}`,
    });
  }

  return targets;
}

function summarizeSyncTargets(): string {
  const labels = [COUCHDB_GLOBAL_DB, ...COUCHDB_SCOPED_DBS].filter(Boolean);
  return labels.length > 0 ? labels.join(", ") : "none";
}

function deriveSyncStatus(summary: SyncSummary): SyncStatus {
  if (summary.total === 0) {
    return "idle";
  }
  if (summary.errored > 0) {
    return "error";
  }
  if (summary.active > 0) {
    return "syncing";
  }
  if (summary.paused === summary.total) {
    return "synced";
  }
  return "idle";
}

function isStoppedSync(info: PouchDB.Replication.SyncResultComplete<PropertyDoc> | undefined): boolean {
  return info?.push?.errors?.length === 0 && info?.pull?.errors?.length === 0;
}

function createInitialSummary(total: number): SyncSummary {
  return {
    total,
    active: 0,
    paused: 0,
    errored: 0,
  };
}

let syncHandles = new Map<string, PouchDB.Replication.Sync<PropertyDoc>>();
let syncSummary = createInitialSummary(0);
let currentStatus: SyncStatus = "idle";
let networkListenersAttached = false;
let syncGeneration = 0;
let currentToken: string | null = null;
let lifecycleRetryDelay = INITIAL_BACKOFF_MS;
let lifecycleRetryTimer: ReturnType<typeof setTimeout> | null = null;

export type SyncStatus =
  | "idle"
  | "syncing"
  | "synced"
  | "error"
  | "offline"
  | "retrying";

interface HealthCheckResult {
  ok: boolean;
  authFailed: boolean;
}

const statusListeners = new Set<(status: SyncStatus) => void>();
const authFailureListeners = new Set<() => void>();

function withJitter(delay: number): number {
  return Math.round(delay * (0.8 + Math.random() * 0.4));
}

function pouchdbBackoff(delay: number): number {
  const base = delay < INITIAL_BACKOFF_MS ? INITIAL_BACKOFF_MS : delay * 2;
  return Math.min(withJitter(base), MAX_BACKOFF_MS);
}

function setStatus(status: SyncStatus) {
  currentStatus = status;
  statusListeners.forEach((fn) => fn(status));
}

function resetSyncHandles() {
  syncHandles.forEach((handle) => handle.cancel());
  syncHandles = new Map();
  syncSummary = createInitialSummary(0);
}

function updateSummaryStatus() {
  setStatus(deriveSyncStatus(syncSummary));
}

function markTargetActive() {
  syncSummary.active += 1;
  if (syncSummary.paused > 0) {
    syncSummary.paused -= 1;
  }
  updateSummaryStatus();
}

function markTargetPaused() {
  if (syncSummary.active > 0) {
    syncSummary.active -= 1;
  }
  syncSummary.paused += 1;
  lifecycleRetryDelay = INITIAL_BACKOFF_MS;
  updateSummaryStatus();
}

function markTargetError() {
  syncSummary.errored += 1;
  if (syncSummary.active > 0) {
    syncSummary.active -= 1;
  }
  updateSummaryStatus();
}

function markTargetComplete() {
  if (syncSummary.active > 0) {
    syncSummary.active -= 1;
  }
  updateSummaryStatus();
}

function replaceHandle(dbName: string, handle: PouchDB.Replication.Sync<PropertyDoc>) {
  syncHandles.get(dbName)?.cancel();
  syncHandles.set(dbName, handle);
}

function buildPropertyIdentity(token: string | null) {
  const claims = parseTokenClaims(token);
  if (!claims) {
    return null;
  }

  return {
    userId: claims.sub,
    username: claims.username,
    tenantId: claims.tenant_id,
    tenantCode: claims.tenant_code,
  };
}

export function getPropertyIdentity() {
  return buildPropertyIdentity(currentToken);
}

export function getScopedTenantId() {
  return buildPropertyIdentity(currentToken)?.tenantId ?? null;
}

function emitAuthFailure() {
  authFailureListeners.forEach((fn) => fn());
}

function isAuthStatus(status: number): boolean {
  return status === 401 || status === 403;
}

function isAuthError(error: unknown): boolean {
  if (!error || typeof error !== "object") {
    return false;
  }

  const candidate = error as {
    status?: unknown;
    name?: unknown;
    message?: unknown;
    error?: unknown;
  };

  if (typeof candidate.status === "number" && isAuthStatus(candidate.status)) {
    return true;
  }

  const text = [candidate.name, candidate.message, candidate.error]
    .filter((value): value is string => typeof value === "string")
    .join(" ")
    .toLowerCase();

  return text.includes("unauthorized") || text.includes("forbidden") || text.includes("invalid token");
}

export function getSyncStatus(): SyncStatus {
  return currentStatus;
}

export function onSyncStatusChange(fn: (status: SyncStatus) => void): () => void {
  statusListeners.add(fn);
  fn(currentStatus);
  return () => {
    statusListeners.delete(fn);
  };
}

export function onSyncAuthFailure(fn: () => void): () => void {
  authFailureListeners.add(fn);
  return () => {
    authFailureListeners.delete(fn);
  };
}

function resetLifecycleRetry() {
  if (lifecycleRetryTimer) {
    clearTimeout(lifecycleRetryTimer);
    lifecycleRetryTimer = null;
  }
  lifecycleRetryDelay = INITIAL_BACKOFF_MS;
}

function scheduleLifecycleRestart(generation: number) {
  resetLifecycleRetry();

  const delay = withJitter(lifecycleRetryDelay);
  const seconds = (delay / 1000).toFixed(1);
  console.log(`[sync] CouchDB unreachable — retrying in ~${seconds}s`);
  setStatus("retrying");

  lifecycleRetryTimer = setTimeout(() => {
    lifecycleRetryTimer = null;
    if (generation === syncGeneration) {
      startSync(currentToken);
    }
  }, delay);

  lifecycleRetryDelay = Math.min(lifecycleRetryDelay * 2, MAX_BACKOFF_MS);
}

function attachNetworkListeners() {
  if (networkListenersAttached || typeof window === "undefined") return;
  networkListenersAttached = true;

  window.addEventListener("offline", () => {
    console.log("[sync] Browser went offline");
    resetLifecycleRetry();
    setStatus("offline");
  });

  window.addEventListener("online", () => {
    console.log("[sync] Browser back online — checking CouchDB before restarting");
    resetLifecycleRetry();
    startSync(currentToken);
  });
}

async function checkCouchDbHealth(token: string | null): Promise<HealthCheckResult> {
  if (typeof window === "undefined") {
    return { ok: false, authFailed: false };
  }

  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), HEALTH_CHECK_TIMEOUT_MS);

    const headers: Record<string, string> = {};
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const { origin } = new URL(COUCHDB_URL);
    const response = await fetch(`${origin}/db/_up`, {
      method: "GET",
      headers,
      signal: controller.signal,
    });

    clearTimeout(timer);

    if (isAuthStatus(response.status)) {
      return { ok: false, authFailed: true };
    }

    return { ok: response.ok, authFailed: false };
  } catch {
    return { ok: false, authFailed: false };
  }
}

async function refreshTokenForSync(): Promise<string | null> {
  const result = await refreshAccessTokenClient();
  if (result.success) {
    currentToken = result.accessToken;
    return result.accessToken;
  }

  if (result.terminal) {
    console.warn("[sync] Session refresh failed permanently");
    currentToken = null;
    emitAuthFailure();
    return null;
  }

  console.warn("[sync] Session refresh unavailable; will retry later");
  return null;
}

async function ensureHealthyConnection(token: string | null): Promise<{ ok: boolean; token: string | null; }> {
  const initial = await checkCouchDbHealth(token);
  if (initial.ok) {
    return { ok: true, token };
  }

  if (!initial.authFailed) {
    return { ok: false, token };
  }

  const refreshedToken = await refreshTokenForSync();
  if (!refreshedToken) {
    return { ok: false, token: null };
  }

  const afterRefresh = await checkCouchDbHealth(refreshedToken);
  if (afterRefresh.ok) {
    return { ok: true, token: refreshedToken };
  }

  if (afterRefresh.authFailed) {
    emitAuthFailure();
    currentToken = null;
    return { ok: false, token: null };
  }

  return { ok: false, token: refreshedToken };
}

export function startSync(token: string | null) {
  currentToken = token;
  const generation = ++syncGeneration;

  resetLifecycleRetry();
  resetSyncHandles();

  attachNetworkListeners();

  if (typeof window !== "undefined" && !navigator.onLine) {
    console.log("[sync] Browser offline — deferring sync until online");
    setStatus("offline");
    return;
  }

  const targets = buildSyncTargets(token);
  if (targets.length === 0) {
    console.warn(`[sync] No remote databases configured. Global=${COUCHDB_GLOBAL_DB || "<empty>"}; scoped=${summarizeSyncTargets()}`);
    setStatus("idle");
    return;
  }

  setStatus("syncing");

  ensureHealthyConnection(token).then(({ ok, token: nextToken }) => {
    if (generation !== syncGeneration) return;

    if (!ok) {
      if (!nextToken) {
        setStatus("error");
        return;
      }

      scheduleLifecycleRestart(generation);
      return;
    }

    currentToken = nextToken;
    connectSyncTargets(generation, nextToken, buildSyncTargets(nextToken));
  });
}

function connectSyncTargets(generation: number, token: string | null, targets: SyncTarget[]) {
  syncSummary = createInitialSummary(targets.length);

  for (const target of targets) {
    console.log("[sync] Starting live sync →", target.remoteUrl, token ? "(authenticated)" : "(anonymous)");

    const remoteDb = new PouchDB<PropertyDoc>(target.remoteUrl, {
      fetch: async (url, opts = {}) => {
        const safeUrl = typeof url === "string" || url instanceof URL ? url : String(url);

        const attemptFetch = async (bearerToken: string | null) => {
          const headers = new Headers(opts.headers || {});
          if (bearerToken) {
            headers.set("Authorization", `Bearer ${bearerToken}`);
          } else {
            headers.delete("Authorization");
          }

          return PouchDB.fetch(safeUrl, {
            ...opts,
            headers,
            credentials: "include",
          });
        };

        const firstResponse = await attemptFetch(currentToken ?? token);
        if (!isAuthStatus(firstResponse.status)) {
          return firstResponse;
        }

        const refreshedToken = await refreshTokenForSync();
        if (!refreshedToken) {
          return firstResponse;
        }

        return attemptFetch(refreshedToken);
      },
    });

    const handle = getDb().sync<PropertyDoc>(remoteDb, {
      live: true,
      retry: true,
      heartbeat: 10_000,
      back_off_function: pouchdbBackoff,
    });

    replaceHandle(target.dbName, handle);

    handle.on("paused", (err) => {
      if (generation !== syncGeneration) return;
      if (err) {
        console.error(`[sync] ${target.dbName} paused with error:`, err);
        if (isAuthError(err)) {
          emitAuthFailure();
        }
        markTargetError();
      } else {
        console.log(`[sync] ${target.dbName} up to date — paused`);
        markTargetPaused();
      }
    });

    handle.on("active", () => {
      if (generation !== syncGeneration) return;
      console.log(`[sync] ${target.dbName} data transfer active`);
      markTargetActive();
    });

    handle.on("denied", (err) => {
      if (generation !== syncGeneration) return;
      console.error(`[sync] ${target.dbName} document denied:`, err);
      if (isAuthError(err)) {
        emitAuthFailure();
      }
      markTargetError();
    });

    handle.on("error", (err) => {
      if (generation !== syncGeneration) return;
      console.error(`[sync] ${target.dbName} sync error (PouchDB will retry):`, err instanceof Error ? err.message : err);
      if (isAuthError(err)) {
        emitAuthFailure();
      }
      markTargetError();
    });

    handle.on("complete", (info) => {
      if (generation !== syncGeneration) return;
      syncHandles.delete(target.dbName);

      if (isStoppedSync(info)) {
        console.log(`[sync] ${target.dbName} sync completed normally`);
        markTargetComplete();
      } else {
        scheduleLifecycleRestart(generation);
      }
    });
  }
}

export async function stopSync(): Promise<void> {
  syncGeneration++;
  resetLifecycleRetry();
  currentToken = null;
  resetSyncHandles();
  setStatus("idle");
}

export async function resolveConflicts(docId: string): Promise<void> {
  try {
    const doc = await getDb().get(docId, { conflicts: true });
    if (!doc._conflicts || doc._conflicts.length === 0) return;

    const conflictRevs = doc._conflicts;
    const all = await Promise.all([
      doc,
      ...conflictRevs.map((rev: string) => getDb().get(docId, { rev })),
    ]);

    const winner = all.reduce((a, b) =>
      new Date(a.updated_at).getTime() >= new Date(b.updated_at).getTime() ? a : b,
    );

    await Promise.all(
      all
        .filter((d) => d._rev !== winner._rev)
        .map((d) => getDb().remove(d._id, d._rev!)),
    );

    console.log(`[conflict] Resolved ${docId}: kept rev ${winner._rev}`);
  } catch (err) {
    console.error(`[conflict] Failed to resolve ${docId}:`, err);
  }
}

export async function resolveAllConflicts(): Promise<void> {
  const result = await getDb().allDocs({
    include_docs: true,
    conflicts: true,
  });

  const conflicting = result.rows.filter(
    (row) => (row as { doc?: { _conflicts?: string[] } }).doc?._conflicts?.length,
  );

  await Promise.all(conflicting.map((row) => resolveConflicts(row.id)));
}
