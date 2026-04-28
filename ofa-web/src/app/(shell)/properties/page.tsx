"use client";

import dynamic from "next/dynamic";

const PropertyClient = dynamic(
  () => import("@/app/(properties)/property-client"),
  { ssr: false },
);

export default function PropertiesPage() {
  return <PropertyClient />;
}