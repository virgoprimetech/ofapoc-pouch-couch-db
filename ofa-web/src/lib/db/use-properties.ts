"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { ensureIndex, getDb } from "./index";
import { propertyRepository } from "./property-repository";
import type {
  PropertyDoc,
  CreatePropertyInput,
  UpdatePropertyInput,
  PropertyStatus,
  PropertyIdentity,
} from "./schema";

// --- useProperties: reactive property list ---

export function useProperties(tenantId?: string) {
  const [properties, setProperties] = useState<PropertyDoc[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const changesRef = useRef<PouchDB.Core.Changes<PropertyDoc> | null>(null);

  const fetchAll = useCallback(async () => {
    const result = await propertyRepository.getAll(tenantId);
    if (result.success) {
      setProperties(result.data);
      setError(null);
    } else {
      setError(result.error.message);
    }
    setIsLoading(false);
  }, [tenantId]);

  // Initial load + live changes feed
  useEffect(() => {
    let cancelled = false;

    async function init() {
      await ensureIndex();
      if (cancelled) return;
      await fetchAll();
      if (cancelled) return;

      changesRef.current = getDb()
        .changes({
          since: "now",
          live: true,
          include_docs: true,
          filter: (doc: PropertyDoc) =>
            doc.type === "property" && !doc.deleted_at,
        })
        .on("change", () => {
          if (!cancelled) fetchAll();
        })
        .on("error", (err: Error) => {
          console.error("[useProperties] Changes feed error:", err);
        });
    }

    init();

    return () => {
      cancelled = true;
      changesRef.current?.cancel();
      changesRef.current = null;
    };
  }, [fetchAll]);

  const create = useCallback(
    async (input: CreatePropertyInput, identity: PropertyIdentity) => {
      const result = await propertyRepository.create(input, identity);
      if (result.success) await fetchAll();
      return result;
    },
    [fetchAll],
  );

  const update = useCallback(
    async (id: string, changes: UpdatePropertyInput & { status?: PropertyStatus }) => {
      const result = await propertyRepository.update(id, changes);
      if (result.success) await fetchAll();
      return result;
    },
    [fetchAll],
  );

  const softDelete = useCallback(
    async (id: string) => {
      const result = await propertyRepository.softDelete(id);
      if (result.success) await fetchAll();
      return result;
    },
    [fetchAll],
  );

  return { properties, isLoading, error, create, update, softDelete, refetch: fetchAll };
}

// --- useOnline: detect network state ---

export function useOnline(): boolean {
  const [online, setOnline] = useState(() =>
    typeof navigator === "undefined" ? true : navigator.onLine,
  );

  useEffect(() => {

    const onOnline = () => setOnline(true);
    const onOffline = () => setOnline(false);

    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);

    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, []);

  return online;
}
