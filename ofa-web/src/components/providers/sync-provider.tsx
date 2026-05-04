"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { ensureIndex } from "@/lib/db/index";
import {
  startSync,
  stopSync,
  onSyncAuthFailure,
  onSyncStatusChange,
  type SyncStatus,
} from "@/lib/db/sync";
import { useAuthStore } from "@/features/auth/store";
import { getAccessTokenAction } from "@/features/auth/actions";

const SyncStatusContext = createContext<SyncStatus>("idle");

export function useSyncStatusContext(): SyncStatus {
  return useContext(SyncStatusContext);
}

export function SyncProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<SyncStatus>("idle");
  const accessToken = useAuthStore((s) => s.accessToken);
  const setAccessToken = useAuthStore((s) => s.setAccessToken);
  const clearAuth = useAuthStore((s) => s.clear);
  const hydrated = useAuthStore((s) => s.hydrated);
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    const unsubscribe = onSyncAuthFailure(() => {
      clearAuth();
      void stopSync();
    });

    return unsubscribe;
  }, [clearAuth]);


  // On mount: rehydrate the access token from the httpOnly cookie
  // (accessToken is not persisted to localStorage for security)
  useEffect(() => {
    if (!hydrated || !user) return;

    // If we already have a token (from login), no need to fetch
    if (accessToken) return;

    let cancelled = false;
    getAccessTokenAction().then((token) => {
      if (!cancelled && token) {
        setAccessToken(token);
      }
    });

    return () => { cancelled = true; };
  }, [hydrated, user, accessToken, setAccessToken]);

  // Start/stop sync based on token availability
  useEffect(() => {
    if (!hydrated) return;
    if (!user) return;

    // 1. Create indexes (idempotent — skips if already created)
    ensureIndex();

    // 2. Subscribe to status changes
    const unsubscribe = onSyncStatusChange(setStatus);

    // 3. Start live sync with the Bearer token
    startSync(accessToken);

    return () => {
      unsubscribe();
      void stopSync();
    };
  }, [hydrated, user, accessToken]);

  return <SyncStatusContext value={status}>{children}</SyncStatusContext>;
}