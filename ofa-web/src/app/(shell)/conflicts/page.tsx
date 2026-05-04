"use client";

import dynamic from "next/dynamic";

const ConflictClient = dynamic(
  () => import("@/app/(conflicts)/conflict-client"),
  { ssr: false },
);

export default function ConflictsPage() {
  return <ConflictClient />;
}