"use client";

import { useState } from "react";
import { useConflicts } from "@/lib/db/use-conflicts";
import { ConflictListTable } from "@/components/conflicts/conflict-list-table";
import { ConflictDetailPanel } from "@/components/conflicts/conflict-detail-panel";
import { Skeleton } from "@/components/ui/skeleton";
import { RiAlertLine } from "@remixicon/react";
import type { ConflictEntry } from "@/lib/db/conflict";

export default function ConflictClient() {
  const { conflicts, isLoading, error, resolveQuick, resolveAdvanced } = useConflicts();
  const [selected, setSelected] = useState<ConflictEntry | null>(null);
  const [typeFilter, setTypeFilter] = useState("all");

  if (isLoading) {
    return (
      <div className="p-6 space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6">
        <p className="text-destructive">Failed to load conflicts: {error}</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b">
        <div className="flex items-center gap-2">
          <RiAlertLine className="size-5 text-destructive" />
          <h1 className="text-lg font-semibold">Conflict Resolution</h1>
        </div>
        <p className="text-sm text-muted-foreground mt-1">
          {conflicts.length === 0
            ? "No unresolved conflicts"
            : `${conflicts.length} unresolved conflict${conflicts.length > 1 ? "s" : ""}`}
        </p>
      </div>

      {/* Content: list + detail */}
      <div className="flex-1 flex min-h-0">
        {/* Left: conflict list */}
        <div className={`p-4 overflow-auto ${selected ? "w-[55%] border-r" : "w-full"}`}>
          <ConflictListTable
            conflicts={conflicts}
            selectedId={selected?.docId ?? null}
            onSelect={setSelected}
            typeFilter={typeFilter}
            onTypeFilterChange={setTypeFilter}
          />
        </div>

        {/* Right: detail panel */}
        {selected && (
          <div className="w-[45%] p-4 overflow-auto">
            <ConflictDetailPanel
              entry={selected}
              onResolveQuick={resolveQuick}
              onResolveAdvanced={resolveAdvanced}
              onClose={() => setSelected(null)}
            />
          </div>
        )}
      </div>
    </div>
  );
}
