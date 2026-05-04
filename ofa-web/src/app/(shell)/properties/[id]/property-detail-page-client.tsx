"use client";

import dynamic from "next/dynamic";

const PropertyDetailClient = dynamic(
  () => import("@/app/(properties)/property-detail-client"),
  { ssr: false },
);

export function PropertyDetailPageClient({ id }: { id: string }) {
  return <PropertyDetailClient id={id} />;
}
