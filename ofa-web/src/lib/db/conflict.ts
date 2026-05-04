"use client";

import { getDb } from "./index";
import { docTypeFromId } from "./schema";

// --- Types ---

export interface ConflictRevision {
  rev: string;
  doc: Record<string, unknown>;
  updated_at: string;
  updated_by: string;
}

export interface ConflictEntry {
  docId: string;
  docType: string;
  winningRev: string;
  revisions: ConflictRevision[];
}

export interface FieldDiff {
  field: string;
  hasConflict: boolean;
  values: { rev: string; value: unknown; updated_by: string }[];
}

function getRawDb(): PouchDB.Database<Record<string, unknown>> {
  return getDb() as unknown as PouchDB.Database<Record<string, unknown>>;
}

// --- Detection ---

/** Scan all documents for unresolved conflicts */
export async function getAllConflicts(): Promise<ConflictEntry[]> {
  const result = await getRawDb().allDocs({
    include_docs: true,
    conflicts: true,
  });

  const entries: ConflictEntry[] = [];

  for (const row of result.rows) {
    const doc = row.doc as (Record<string, unknown> & { _conflicts?: string[] }) | null;
    if (!doc?._conflicts?.length) continue;

    // Fetch all conflicting revisions
    const conflictDocs = await Promise.all(
      doc._conflicts.map((rev: string) =>
        getRawDb().get(row.id, { rev }),
      ),
    );

    const allDocs = [doc, ...conflictDocs];

    entries.push({
      docId: row.id,
      docType: (doc.type as string) ?? docTypeFromId(row.id),
      winningRev: doc._rev as string,
      revisions: allDocs.map((d) => ({
        rev: d._rev as string,
        doc: d,
        updated_at: (d.updated_at as string) ?? "",
        updated_by: (d.updated_by as string) ?? "system",
      })),
    });
  }

  return entries;
}

// --- Diff Calculation ---

const SYSTEM_FIELDS = new Set(["_id", "_rev", "_conflicts", "_attachments"]);

/** Compare all revisions field-by-field, conflicted fields first */
export function computeFieldDiffs(revisions: ConflictRevision[]): FieldDiff[] {
  if (revisions.length === 0) return [];

  const allFields = new Set<string>();
  for (const rev of revisions) {
    for (const key of Object.keys(rev.doc)) {
      if (!SYSTEM_FIELDS.has(key)) allFields.add(key);
    }
  }

  const diffs: FieldDiff[] = [];

  for (const field of allFields) {
    const values = revisions.map((r) => ({
      rev: r.rev,
      value: r.doc[field],
      updated_by: r.updated_by,
    }));

    const uniqueValues = new Set(values.map((v) => JSON.stringify(v.value)));

    diffs.push({
      field,
      hasConflict: uniqueValues.size > 1,
      values,
    });
  }

  return diffs.sort((a, b) => {
    if (a.hasConflict !== b.hasConflict) return a.hasConflict ? -1 : 1;
    return a.field.localeCompare(b.field);
  });
}

// --- Resolution ---

/** Quick: pick one revision as winner, delete the rest */
export async function resolveByRevision(
  docId: string,
  winnerRev: string,
): Promise<void> {
  const doc = await getRawDb().get(docId, { conflicts: true }) as Record<string, unknown> & { _conflicts?: string[] };
  if (!doc._conflicts?.length) return;

  const losingRevs = doc._conflicts.filter(
    (rev: string) => rev !== winnerRev,
  );

  // If winner is a conflict rev (not the winning rev), promote it
  if (doc._rev !== winnerRev) {
    const winnerDoc = await getRawDb().get(docId, { rev: winnerRev });
    await getRawDb().put({ ...winnerDoc, _rev: doc._rev as string });
  }

  // Delete all other conflict revisions
  await Promise.all(
    losingRevs.map((rev: string) =>
      getRawDb().remove({ _id: docId, _rev: rev }),
    ),
  );

  console.log(`[conflict] Resolved ${docId}: kept rev ${winnerRev}`);
}

/** Advanced: build merged doc from per-field picks, write as update, delete losers */
export async function resolveByFieldMerge(
  docId: string,
  fieldPicks: Record<string, string>, // { fieldName: revToUse }
): Promise<void> {
  const doc = await getRawDb().get(docId, { conflicts: true }) as Record<string, unknown> & { _conflicts?: string[] };
  if (!doc._conflicts?.length) return;

  // Fetch all revisions
  const allRevs: Record<string, Record<string, unknown>> = {};
  allRevs[doc._rev as string] = doc;
  for (const rev of doc._conflicts) {
    allRevs[rev] = await getRawDb().get(docId, { rev });
  }

  // Build merged document from field picks
  const merged = { ...doc };
  for (const [field, pickRev] of Object.entries(fieldPicks)) {
    const source = allRevs[pickRev];
    if (source) {
      merged[field] = source[field];
    }
  }

  merged.updated_at = new Date().toISOString();
  merged.updated_by = "conflict-resolver";

  // Write merged doc (updates the winning rev)
  await getRawDb().put(merged);

  // Delete all conflict revs
  await Promise.all(
    doc._conflicts.map((rev: string) =>
      getRawDb().remove({ _id: docId, _rev: rev }),
    ),
  );

  console.log(`[conflict] Field-merged ${docId}`);
}
