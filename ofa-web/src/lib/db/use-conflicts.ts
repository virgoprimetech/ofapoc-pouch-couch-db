"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { getDb } from "./index";
import {
  getAllConflicts,
  resolveByRevision,
  resolveByFieldMerge,
  type ConflictEntry,
} from "./conflict";

export function useConflicts() {
  const [conflicts, setConflicts] = useState<ConflictEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const changesRef = useRef<PouchDB.Core.Changes<Record<string, unknown>> | null>(null);

  const fetchAll = useCallback(async () => {
    try {
      const entries = await getAllConflicts();
      setConflicts(entries);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load conflicts");
    }
    setIsLoading(false);
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function init() {
      await fetchAll();
      if (cancelled) return;

      changesRef.current = getDb()
        .changes({ since: "now", live: true })
        .on("change", () => {
          if (!cancelled) fetchAll();
        })
        .on("error", (err: Error) => {
          console.error("[useConflicts] Changes feed error:", err);
        });
    }

    init();

    return () => {
      cancelled = true;
      changesRef.current?.cancel();
      changesRef.current = null;
    };
  }, [fetchAll]);

  const resolveQuick = useCallback(
    async (docId: string, winnerRev: string) => {
      await resolveByRevision(docId, winnerRev);
      await fetchAll();
    },
    [fetchAll],
  );

  const resolveAdvanced = useCallback(
    async (docId: string, fieldPicks: Record<string, string>) => {
      await resolveByFieldMerge(docId, fieldPicks);
      await fetchAll();
    },
    [fetchAll],
  );

  return {
    conflicts,
    isLoading,
    error,
    resolveQuick,
    resolveAdvanced,
    refetch: fetchAll,
  };
}
