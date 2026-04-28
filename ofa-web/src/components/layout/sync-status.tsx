"use client";

import { useSyncStatusContext } from "@/components/providers/sync-provider";
import { useOnline } from "@/lib/db/use-properties";
import {
  RiWifiLine,
  RiWifiOffLine,
  RiRefreshLine,
} from "@remixicon/react";

export function SyncStatusIcon() {
  const syncStatus = useSyncStatusContext();
  const isOnline = useOnline();

  if (!isOnline) {
    return (
      <span title="Offline" className="flex items-center gap-1.5 text-xs text-destructive">
        <RiWifiOffLine className="size-3.5" />
        <span className="hidden group-data-[collapsible=icon]:hidden lg:inline">Offline</span>
      </span>
    );
  }

  switch (syncStatus) {
    case "syncing":
      return (
        <span title="Syncing..." className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <RiRefreshLine className="size-3.5 motion-safe:animate-spin" />
          <span className="hidden group-data-[collapsible=icon]:hidden lg:inline">Syncing</span>
        </span>
      );
    case "retrying":
      return (
        <span title="Connection lost — retrying..." className="flex items-center gap-1.5 text-xs text-amber-600">
          <RiRefreshLine className="size-3.5 motion-safe:animate-spin" />
          <span className="hidden group-data-[collapsible=icon]:hidden lg:inline">Retrying</span>
        </span>
      );
    case "synced":
      return (
        <span title="Synced" className="flex items-center gap-1.5 text-xs text-primary">
          <RiWifiLine className="size-3.5" />
          <span className="hidden group-data-[collapsible=icon]:hidden lg:inline">Synced</span>
        </span>
      );
    case "error":
      return (
        <span title="Sync error" className="flex items-center gap-1.5 text-xs text-destructive">
          <RiWifiOffLine className="size-3.5" />
          <span className="hidden group-data-[collapsible=icon]:hidden lg:inline">Error</span>
        </span>
      );
    default:
      return (
        <span title="Idle" className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <RiWifiLine className="size-3.5" />
          <span className="hidden group-data-[collapsible=icon]:hidden lg:inline">Idle</span>
        </span>
      );
  }
}
