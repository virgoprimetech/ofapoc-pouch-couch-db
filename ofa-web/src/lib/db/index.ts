"use client";

import PouchDB from "pouchdb-browser";
import PouchFind from "pouchdb-find";
import type { PropertyDoc } from "./schema";

PouchDB.plugin(PouchFind);

type PropertyDb = PouchDB.Database<PropertyDoc>;

let db: PropertyDb | null = null;

// Ensure the Mango query index exists for the type discriminator
let indexCreated = false;

function createDb(): PropertyDb {
  return new PouchDB<PropertyDoc>("properties", {
    auto_compaction: true,
    revs_limit: 10,
  });
}

export function getDb(): PropertyDb {
  if (!db) {
    db = createDb();
  }

  return db;
}

export async function ensureIndex() {
  if (indexCreated) return;

  await getDb().createIndex({
    index: {
      fields: ["type", "status", "tenant_id", "updated_at", "deleted_at"],
      name: "property-main-index",
    },
  });
  indexCreated = true;
}

export async function destroyDb(): Promise<void> {
  const currentDb = db;
  db = null;
  indexCreated = false;

  if (!currentDb) {
    return;
  }

  await currentDb.destroy();
}
