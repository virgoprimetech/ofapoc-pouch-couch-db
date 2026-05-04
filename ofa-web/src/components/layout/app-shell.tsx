"use client";

import * as React from "react";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarProvider,
  SidebarInset,
  SidebarRail,
  SidebarTrigger,
  SidebarSeparator,
} from "@/components/ui/sidebar";
import { Separator } from "@/components/ui/separator";
import {
  RiBuilding2Line,
  RiDashboard3Line,
  RiSettings4Line,
  RiCalendarScheduleLine,
  RiGroupLine,
  RiMoneyDollarCircleLine,
  RiHome5Line,
  RiAlertLine,
} from "@remixicon/react";
import { TooltipProvider } from "@/components/ui/tooltip";
import { UserMenu } from "@/components/layout/user-menu";
import Link from "next/link";

// Navigation items — PMS domain structure
const NAV_ITEMS = [
  { label: "Dashboard", icon: RiDashboard3Line, href: "/", disabled: true },
  { label: "Properties", icon: RiHome5Line, href: "/properties", active: true },
  { label: "Reservations", icon: RiCalendarScheduleLine, href: "/", disabled: true },
  { label: "Guests", icon: RiGroupLine, href: "/", disabled: true },
  { label: "Billing", icon: RiMoneyDollarCircleLine, href: "/", disabled: true },
  { label: "Conflicts", icon: RiAlertLine, href: "/conflicts" },
  { label: "Settings", icon: RiSettings4Line, href: "/", disabled: true },
];

// Sync status loaded lazily to avoid PouchDB SSR crash
const LazySyncStatus = React.lazy(() =>
  import("./sync-status").then((mod) => ({ default: mod.SyncStatusIcon }))
);

function AppSidebar() {
  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" className="cursor-default">
              <div className="flex aspect-square size-8 items-center justify-center rounded-sm bg-primary text-primary-foreground">
                <RiBuilding2Line className="size-4" />
              </div>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-semibold">OFA PMS</span>
                <span className="truncate text-xs text-muted-foreground">Property Manager</span>
              </div>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarSeparator />

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Navigation</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {NAV_ITEMS.map((item) => (
                <SidebarMenuItem key={item.label}>
                  <SidebarMenuButton
                    asChild={!item.disabled}
                    isActive={item.active}
                    disabled={item.disabled}
                    tooltip={item.label}
                    className={item.active ? "cursor-pointer" : item.disabled ? "cursor-not-allowed opacity-50" : "cursor-pointer"}
                  >
                    {!item.disabled ? (
                      <Link href={item.href}>
                        <item.icon className="size-4" />
                        <span>{item.label}</span>
                      </Link>
                    ) : (
                      <>
                        <item.icon className="size-4" />
                        <span>{item.label}</span>
                      </>
                    )}
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>
        <SidebarSeparator />
        <div className="px-2 py-1">
          <React.Suspense fallback={<span className="text-xs text-muted-foreground">...</span>}>
            <LazySyncStatus />
          </React.Suspense>
        </div>
      </SidebarFooter>

      <SidebarRail />
    </Sidebar>
  );
}

function AppHeader() {
  return (
    <header className="flex h-12 shrink-0 items-center gap-2 border-b px-4">
      <SidebarTrigger className="-ml-1 cursor-pointer" />
      <Separator orientation="vertical" className="mr-2 h-4" />
      <div className="flex flex-1 items-center gap-2">
        <RiBuilding2Line className="size-4 text-primary" />
        <span className="text-sm font-medium">Property Management</span>
      </div>
      <UserMenu />
    </header>
  );
}

function AppFooter() {
  return (
    <footer className="flex h-8 shrink-0 items-center border-t px-4 text-xs text-muted-foreground">
      <span>OFA PMS v0.1.0</span>
      <span className="mx-2">&middot;</span>
      <span>Offline-first with PouchDB</span>
    </footer>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <TooltipProvider>
      <SidebarProvider>
        <AppSidebar />
        <SidebarInset>
          <AppHeader />
            <main className="flex-1 overflow-auto">{children}</main>
          <AppFooter />
        </SidebarInset>
      </SidebarProvider>
    </TooltipProvider>
  );
}
