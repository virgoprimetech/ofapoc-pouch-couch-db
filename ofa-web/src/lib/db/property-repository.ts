"use client";

import { ensureIndex, getDb } from "./index";
import { resolveConflicts } from "./sync";
import {
  type PropertyDoc,
  type CreatePropertyInput,
  type UpdatePropertyInput,
  type PropertyStatus,
  type PropertyIdentity,
  CreatePropertyInputSchema,
  UpdatePropertyInputSchema,
  createPropertyDoc,
  propertyId,
} from "./schema";

// --- Result type (services never throw) ---

type Ok<T> = { success: true; data: T };
type Err<E> = { success: false; error: E };
type Result<T, E> = Ok<T> | Err<E>;

// --- Error types ---

type CreateError =
  | { kind: "VALIDATION"; message: string }
  | { kind: "DB_ERROR"; message: string };

type ReadError =
  | { kind: "NOT_FOUND"; message: string }
  | { kind: "DB_ERROR"; message: string };

type UpdateError =
  | { kind: "NOT_FOUND"; message: string }
  | { kind: "VALIDATION"; message: string }
  | { kind: "INVALID_TRANSITION"; from: PropertyStatus; to: PropertyStatus }
  | { kind: "ACTIVATION_REQUIREMENTS"; missing: string[] }
  | { kind: "CONFLICT"; message: string }
  | { kind: "DB_ERROR"; message: string };

type DeleteError =
  | { kind: "NOT_FOUND"; message: string }
  | { kind: "CONFLICT"; message: string }
  | { kind: "DB_ERROR"; message: string };

// --- Repository ---

export const propertyRepository = {
  // -- CREATE --

  async create(
    input: CreatePropertyInput,
    identity: PropertyIdentity,
  ): Promise<Result<PropertyDoc, CreateError>> {
    const parsed = CreatePropertyInputSchema.safeParse(input);
    if (!parsed.success) {
      return {
        success: false,
        error: {
          kind: "VALIDATION",
          message: parsed.error.issues.map((i) => i.message).join(", "),
        },
      };
    }

    const doc = createPropertyDoc(parsed.data, identity);

    try {
      await ensureIndex();
      const response = await getDb().put(doc);
      return {
        success: true,
        data: { ...doc, _rev: response.rev },
      };
    } catch (err) {
      return {
        success: false,
        error: {
          kind: "DB_ERROR",
          message: err instanceof Error ? err.message : "Unknown error",
        },
      };
    }
  },

  // -- READ --

  async getById(id: string): Promise<Result<PropertyDoc, ReadError>> {
    // Accept both raw id and prefixed id
    const docId = id.startsWith("property::") ? id : propertyId(id);

    try {
      const doc = await getDb().get(docId);
      if (doc.deleted_at) {
        return {
          success: false,
          error: { kind: "NOT_FOUND", message: "Property was deleted" },
        };
      }
      return { success: true, data: doc };
    } catch (err) {
      if (
        err instanceof Error &&
        (err as { status?: number }).status === 404
      ) {
        return {
          success: false,
          error: { kind: "NOT_FOUND", message: `Property ${id} not found` },
        };
      }
      return {
        success: false,
        error: {
          kind: "DB_ERROR",
          message: err instanceof Error ? err.message : "Unknown error",
        },
      };
    }
  },

  async getAll(tenantId?: string): Promise<Result<PropertyDoc[], ReadError>> {
    try {
      await ensureIndex();

      // Use allDocs with prefix range for efficient retrieval
      const result = await getDb().allDocs({
        startkey: "property::",
        endkey: "property::\uffff",
        include_docs: true,
      });

      const docs = result.rows
        .map((row) => row.doc as PropertyDoc)
        .filter((doc): doc is PropertyDoc => {
          if (!doc) return false;
          if (doc.type !== "property") return false;
          if (doc.deleted_at) return false;
          if (tenantId && doc.tenant_id !== tenantId) return false;
          return true;
        });

      // Sort by updated_at descending
      docs.sort(
        (a, b) =>
          new Date(b.updated_at).getTime() - new Date(a.updated_at).getTime(),
      );

      return { success: true, data: docs };
    } catch (err) {
      return {
        success: false,
        error: {
          kind: "DB_ERROR",
          message: err instanceof Error ? err.message : "Unknown error",
        },
      };
    }
  },

  async getByStatus(
    status: PropertyStatus,
    tenantId?: string,
  ): Promise<Result<PropertyDoc[], ReadError>> {
    const allResult = await this.getAll(tenantId);
    if (!allResult.success) return allResult;

    return {
      success: true,
      data: allResult.data.filter((doc) => doc.status === status),
    };
  },

  // -- UPDATE --

  async update(
    id: string,
    changes: UpdatePropertyInput & { status?: PropertyStatus },
  ): Promise<Result<PropertyDoc, UpdateError>> {
    const docId = id.startsWith("property::") ? id : propertyId(id);

    // Validate input
    const parsed = UpdatePropertyInputSchema.safeParse(changes);
    if (!parsed.success) {
      return {
        success: false,
        error: {
          kind: "VALIDATION",
          message: parsed.error.issues.map((i) => i.message).join(", "),
        },
      };
    }

    try {
      const existing = await getDb().get(docId);
      if (existing.deleted_at) {
        return {
          success: false,
          error: { kind: "NOT_FOUND", message: "Property was deleted" },
        };
      }

      // POC: No status transition constraints — any status can be set freely

      // Merge and write
      const updated: PropertyDoc = {
        ...existing,
        ...parsed.data,
        ...(changes.status ? { status: changes.status } : {}),
        _id: existing._id,
        _rev: existing._rev, // required for conflict detection
        type: "property", // never allow overwriting the type
        user_id: existing.user_id,
        username: existing.username,
        tenant_id: existing.tenant_id,
        tenant_code: existing.tenant_code,
        created_at: existing.created_at, // created_at is immutable
        updated_at: new Date().toISOString(),
        updated_by: existing.username,
      };

      const response = await getDb().put(updated);

      // Check for and resolve any conflicts
      await resolveConflicts(docId);

      return {
        success: true,
        data: { ...updated, _rev: response.rev },
      };
    } catch (err) {
      if (
        err instanceof Error &&
        (err as { status?: number }).status === 404
      ) {
        return {
          success: false,
          error: { kind: "NOT_FOUND", message: `Property ${id} not found` },
        };
      }
      if (
        err instanceof Error &&
        (err as { status?: number }).status === 409
      ) {
        return {
          success: false,
          error: { kind: "CONFLICT", message: "Document conflict — will retry on next sync" },
        };
      }
      return {
        success: false,
        error: {
          kind: "DB_ERROR",
          message: err instanceof Error ? err.message : "Unknown error",
        },
      };
    }
  },

  // -- SOFT DELETE --

  async softDelete(id: string): Promise<Result<PropertyDoc, DeleteError>> {
    const docId = id.startsWith("property::") ? id : propertyId(id);

    try {
      const existing = await getDb().get(docId);
      if (existing.deleted_at) {
        return {
          success: false,
          error: { kind: "NOT_FOUND", message: "Property already deleted" },
        };
      }

      const deleted: PropertyDoc = {
        ...existing,
        status: "ARCHIVED",
        deleted_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
        updated_by: existing.username,
      };

      const response = await getDb().put(deleted);

      return {
        success: true,
        data: { ...deleted, _rev: response.rev },
      };
    } catch (err) {
      if (
        err instanceof Error &&
        (err as { status?: number }).status === 404
      ) {
        return {
          success: false,
          error: { kind: "NOT_FOUND", message: `Property ${id} not found` },
        };
      }
      if (
        err instanceof Error &&
        (err as { status?: number }).status === 409
      ) {
        return {
          success: false,
          error: { kind: "CONFLICT", message: "Document conflict — will retry on next sync" },
        };
      }
      return {
        success: false,
        error: {
          kind: "DB_ERROR",
          message: err instanceof Error ? err.message : "Unknown error",
        },
      };
    }
  },
};
