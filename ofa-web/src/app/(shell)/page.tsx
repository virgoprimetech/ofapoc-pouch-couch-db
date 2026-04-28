"use client";

import dynamic from "next/dynamic";

const ShellClient = dynamic(
  () => import("./shell-client"),
  { ssr: false },
);

export default function ShellPage() {
  return <ShellClient />;
}
