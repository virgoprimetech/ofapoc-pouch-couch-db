"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import type { ConflictRevision } from "@/lib/db/conflict";

const SYSTEM_FIELDS = new Set(["_id", "_rev", "_conflicts", "_attachments"]);

interface RevisionSideBySideProps {
  docId: string;
  revisions: ConflictRevision[];
  onResolve: (docId: string, winnerRev: string) => Promise<void>;
  onCancel: () => void;
}

export function RevisionSideBySide({
  docId,
  revisions,
  onResolve,
  onCancel,
}: RevisionSideBySideProps) {
  const [selectedRev, setSelectedRev] = useState(revisions[0]?.rev ?? "");
  const [resolving, setResolving] = useState(false);

  // Collect all non-system fields across all revisions
  const allFields = new Set<string>();
  for (const rev of revisions) {
    for (const key of Object.keys(rev.doc)) {
      if (!SYSTEM_FIELDS.has(key)) allFields.add(key);
    }
  }
  const fields = [...allFields].sort();

  async function handleResolve() {
    setResolving(true);
    try {
      await onResolve(docId, selectedRev);
    } finally {
      setResolving(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted-foreground">
        Pick one revision to keep. All others will be deleted.
      </p>

      <div className="flex gap-3 overflow-x-auto pb-2">
        {revisions.map((rev) => {
          const isSelected = selectedRev === rev.rev;
          return (
            <Card
              key={rev.rev}
              className={`min-w-[280px] max-w-[320px] shrink-0 cursor-pointer transition-all ${
                isSelected
                  ? "ring-2 ring-primary shadow-md"
                  : "hover:shadow-sm"
              }`}
              onClick={() => setSelectedRev(rev.rev)}
            >
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-xs font-mono">
                    {rev.rev.slice(0, 16)}…
                  </CardTitle>
                  {rev.rev === revisions[0]?.rev && (
                    <Badge variant="secondary" className="text-[10px]">
                      Winner
                    </Badge>
                  )}
                  {isSelected && (
                    <Badge className="text-[10px]">Selected</Badge>
                  )}
                </div>
                <div className="text-[10px] text-muted-foreground">
                  {rev.updated_at
                    ? new Date(rev.updated_at).toLocaleString()
                    : "—"}{" "}
                  · {rev.updated_by}
                </div>
              </CardHeader>
              <CardContent className="text-xs">
                <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                  {fields.map((field) => (
                    <Fragment key={field}>
                      <dt className="font-medium text-muted-foreground truncate">
                        {field}
                      </dt>
                      <dd className="truncate">
                        {formatValue(rev.doc[field])}
                      </dd>
                    </Fragment>
                  ))}
                </dl>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onCancel} className="cursor-pointer">
          Cancel
        </Button>
        <Button
          onClick={handleResolve}
          disabled={!selectedRev || resolving}
          className="cursor-pointer"
        >
          {resolving ? "Resolving…" : "Keep Selected"}
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

// Need Fragment for the dl layout
import { Fragment } from "react";
