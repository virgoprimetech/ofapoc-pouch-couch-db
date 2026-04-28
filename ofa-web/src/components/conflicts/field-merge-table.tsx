"use client";

import { useState, useMemo } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { computeFieldDiffs, type ConflictRevision } from "@/lib/db/conflict";

interface FieldMergeTableProps {
  docId: string;
  revisions: ConflictRevision[];
  onResolve: (docId: string, fieldPicks: Record<string, string>) => Promise<void>;
  onCancel: () => void;
}

export function FieldMergeTable({
  docId,
  revisions,
  onResolve,
  onCancel,
}: FieldMergeTableProps) {
  const diffs = useMemo(() => computeFieldDiffs(revisions), [revisions]);
  const [picks, setPicks] = useState<Record<string, string>>(() => {
    // Default: pick the winning revision for every field
    const initial: Record<string, string> = {};
    for (const diff of diffs) {
      initial[diff.field] = diff.values[0]?.rev ?? "";
    }
    return initial;
  });
  const [resolving, setResolving] = useState(false);

  function setPick(field: string, rev: string) {
    setPicks((prev) => ({ ...prev, [field]: rev }));
  }

  // Auto-select non-conflicting fields
  const conflictedCount = diffs.filter((d) => d.hasConflict).length;

  async function handleResolve() {
    setResolving(true);
    try {
      await onResolve(docId, picks);
    } finally {
      setResolving(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted-foreground">
        {conflictedCount > 0
          ? `${conflictedCount} conflicting field${conflictedCount > 1 ? "s" : ""}. Pick the correct value for each.`
          : "No conflicting fields — all revisions agree."}
      </p>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[140px]">Field</TableHead>
              {revisions.map((rev) => (
                <TableHead key={rev.rev} className="text-xs">
                  <div>
                    <span className="font-mono">{rev.rev.slice(0, 12)}…</span>
                    <br />
                    <span className="text-muted-foreground">
                      {rev.updated_by} ·{" "}
                      {rev.updated_at
                        ? new Date(rev.updated_at).toLocaleTimeString()
                        : "—"}
                    </span>
                  </div>
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {diffs.map((diff) => (
              <TableRow
                key={diff.field}
                className={diff.hasConflict ? "border-l-2 border-l-destructive" : ""}
              >
                <TableCell
                  className={`font-medium text-xs ${
                    diff.hasConflict ? "text-destructive" : "text-muted-foreground"
                  }`}
                >
                  {diff.field}
                  {!diff.hasConflict && (
                    <span className="ml-1 text-[10px]">(same)</span>
                  )}
                </TableCell>
                {diff.values.map((val) => (
                  <TableCell key={val.rev} className="text-xs">
                    <label className="flex items-start gap-2 cursor-pointer">
                      <input
                        type="radio"
                        name={`pick-${diff.field}`}
                        checked={picks[diff.field] === val.rev}
                        onChange={() => setPick(diff.field, val.rev)}
                        className="mt-0.5 accent-primary"
                      />
                      <span className={diff.hasConflict ? "" : "text-muted-foreground"}>
                        {formatValue(val.value)}
                      </span>
                    </label>
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onCancel} className="cursor-pointer">
          Cancel
        </Button>
        <Button
          onClick={handleResolve}
          disabled={resolving}
          className="cursor-pointer"
        >
          {resolving ? "Merging…" : "Merge & Resolve"}
        </Button>
      </div>
    </div>
  );
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "string") return value || "(empty)";
  if (typeof value === "number") return String(value);
  if (typeof value === "boolean") return value ? "Yes" : "No";
  return JSON.stringify(value);
}
