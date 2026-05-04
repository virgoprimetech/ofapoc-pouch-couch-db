"use client";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { ConflictEntry } from "@/lib/db/conflict";

const TYPE_COLORS: Record<string, string> = {
  property: "bg-blue-100 text-blue-800",
  room: "bg-green-100 text-green-800",
  reservation: "bg-purple-100 text-purple-800",
  guest: "bg-orange-100 text-orange-800",
  rate_plan: "bg-yellow-100 text-yellow-800",
};

function formatTime(iso: string): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

interface ConflictListTableProps {
  conflicts: ConflictEntry[];
  selectedId: string | null;
  onSelect: (entry: ConflictEntry) => void;
  typeFilter: string;
  onTypeFilterChange: (type: string) => void;
}

const DOC_TYPES = ["all", "property", "room", "reservation", "guest", "rate_plan"];

export function ConflictListTable({
  conflicts,
  selectedId,
  onSelect,
  typeFilter,
  onTypeFilterChange,
}: ConflictListTableProps) {
  const filtered =
    typeFilter === "all"
      ? conflicts
      : conflicts.filter((c) => c.docType === typeFilter);

  return (
    <div className="flex flex-col gap-3">
      {/* Type filter tabs */}
      <div className="flex gap-1 overflow-x-auto">
        {DOC_TYPES.map((t) => (
          <Button
            key={t}
            variant={typeFilter === t ? "default" : "outline"}
            size="sm"
            onClick={() => onTypeFilterChange(t)}
            className="cursor-pointer capitalize text-xs"
          >
            {t}
          </Button>
        ))}
      </div>

      {/* Conflict table */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[100px]">Type</TableHead>
              <TableHead>Document ID</TableHead>
              <TableHead className="w-[80px]">Revs</TableHead>
              <TableHead>Last Updated</TableHead>
              <TableHead>Actor</TableHead>
              <TableHead className="w-[80px]">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                  No conflicts found
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((entry) => {
                const isSelected = selectedId === entry.docId;
                const lastRev = entry.revisions[0];
                return (
                  <TableRow
                    key={entry.docId}
                    className={`cursor-pointer ${isSelected ? "bg-muted" : ""}`}
                    onClick={() => onSelect(entry)}
                  >
                    <TableCell>
                      <Badge
                        variant="secondary"
                        className={TYPE_COLORS[entry.docType] ?? ""}
                      >
                        {entry.docType}
                      </Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs truncate max-w-[200px]">
                      {entry.docId}
                    </TableCell>
                    <TableCell className="text-center font-medium">
                      {entry.revisions.length}
                    </TableCell>
                    <TableCell className="text-xs">
                      {formatTime(lastRev?.updated_at ?? "")}
                    </TableCell>
                    <TableCell className="text-xs">
                      {lastRev?.updated_by ?? "system"}
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="outline"
                        size="sm"
                        className="cursor-pointer text-xs"
                        onClick={(e) => {
                          e.stopPropagation();
                          onSelect(entry);
                        }}
                      >
                        Resolve
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
