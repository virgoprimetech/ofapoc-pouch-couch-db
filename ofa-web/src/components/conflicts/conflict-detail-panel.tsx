"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import type { ConflictEntry } from "@/lib/db/conflict";
import { RevisionSideBySide } from "./revision-side-by-side";
import { FieldMergeTable } from "./field-merge-table";

type ResolutionMode = "quick" | "advanced";

interface ConflictDetailPanelProps {
  entry: ConflictEntry;
  onResolveQuick: (docId: string, winnerRev: string) => Promise<void>;
  onResolveAdvanced: (docId: string, fieldPicks: Record<string, string>) => Promise<void>;
  onClose: () => void;
}

export function ConflictDetailPanel({
  entry,
  onResolveQuick,
  onResolveAdvanced,
  onClose,
}: ConflictDetailPanelProps) {
  const [mode, setMode] = useState<ResolutionMode>("quick");

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="font-semibold text-sm">Resolve Conflict</h3>
          <p className="text-xs text-muted-foreground font-mono">{entry.docId}</p>
        </div>
        <div className="flex gap-1">
          <Button
            variant={mode === "quick" ? "default" : "outline"}
            size="sm"
            onClick={() => setMode("quick")}
            className="cursor-pointer text-xs"
          >
            Pick Winner
          </Button>
          <Button
            variant={mode === "advanced" ? "default" : "outline"}
            size="sm"
            onClick={() => setMode("advanced")}
            className="cursor-pointer text-xs"
          >
            Field Merge
          </Button>
        </div>
      </div>

      {/* Resolution content */}
      {mode === "quick" ? (
        <RevisionSideBySide
          docId={entry.docId}
          revisions={entry.revisions}
          onResolve={onResolveQuick}
          onCancel={onClose}
        />
      ) : (
        <FieldMergeTable
          docId={entry.docId}
          revisions={entry.revisions}
          onResolve={onResolveAdvanced}
          onCancel={onClose}
        />
      )}
    </div>
  );
}
