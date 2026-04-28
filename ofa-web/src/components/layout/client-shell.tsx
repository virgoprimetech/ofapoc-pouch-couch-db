"use client";

import dynamic from "next/dynamic";

const SerwistProvider = dynamic(
  () =>
    import("@serwist/turbopack/react").then((mod) => ({
      default: mod.SerwistProvider,
    })),
  { ssr: false },
);

const SyncProvider = dynamic(
  () =>
    import("@/components/providers/sync-provider").then((mod) => ({
      default: mod.SyncProvider,
    })),
  { ssr: false },
);

const AppShell = dynamic(
  () => import("./app-shell").then((mod) => ({ default: mod.AppShell })),
  { ssr: false },
);

const AuthRedirect = dynamic(
  () =>
    import("@/features/auth/auth-redirect").then((mod) => ({
      default: mod.AuthRedirect,
    })),
  { ssr: false },
);

export function ClientShell({ children }: { children: React.ReactNode }) {
  return (
    <SerwistProvider swUrl="/sw/sw.js">
      <SyncProvider>
        <AuthRedirect />
        <AppShell>{children}</AppShell>
      </SyncProvider>
    </SerwistProvider>
  );
}
