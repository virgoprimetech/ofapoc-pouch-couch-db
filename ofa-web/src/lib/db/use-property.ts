"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { getDb } from "./index";
import { propertyRepository } from "./property-repository";
import type {
  PropertyDoc,
  UpdatePropertyInput,
  PropertyStatus,
} from "./schema";

export function useProperty(id: string | undefined) {
  const [property, setProperty] = useState<PropertyDoc | null>(null);
  const [isLoading, setIsLoading] = useState(() => Boolean(id));
  const [error, setError] = useState<string | null>(null);
  const changesRef = useRef<PouchDB.Core.Changes<PropertyDoc> | null>(null);

  const fetchDoc = useCallback(async () => {
    if (!id) {
      setProperty(null);
      setError(null);
      setIsLoading(false);
      return;
    }
    const result = await propertyRepository.getById(id);
    if (result.success) {
      setProperty(result.data);
      setError(null);
    } else {
      setProperty(null);
      setError(result.error.message);
    }
    setIsLoading(false);
  }, [id]);

  useEffect(() => {
    if (!id) {
      return;
    }

    const docId = id;
    let cancelled = false;

    async function init() {
      await fetchDoc();
      if (cancelled) return;

      changesRef.current = getDb()
        .changes({
          since: "now",
          live: true,
          include_docs: true,
          doc_ids: [docId],
        })
        .on("change", () => {
          if (!cancelled) fetchDoc();
        })
        .on("error", (err: Error) => {
          console.error("[useProperty] Changes feed error:", err);
        });
    }

    init();

    return () => {
      cancelled = true;
      changesRef.current?.cancel();
      changesRef.current = null;
    };
  }, [id, fetchDoc]);

  const update = useCallback(
    async (changes: UpdatePropertyInput & { status?: PropertyStatus }) => {
      if (!id) return { success: false as const, error: { kind: "DB_ERROR" as const, message: "No ID" } };
      const result = await propertyRepository.update(id, changes);
      if (result.success) await fetchDoc();
      return result;
    },
    [id, fetchDoc],
  );

  const softDelete = useCallback(async () => {
    if (!id) return { success: false as const, error: { kind: "DB_ERROR" as const, message: "No ID" } };
    const result = await propertyRepository.softDelete(id);
    if (result.success) await fetchDoc();
    return result;
  }, [id, fetchDoc]);

  return { property, isLoading, error, update, softDelete, refetch: fetchDoc };
}
