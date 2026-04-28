import { PropertyDetailPageClient } from "./property-detail-page-client";

export default async function PropertyDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  console.log("Online First Auctions: PropertyDetailPage rendered with params:", { id });
  return <PropertyDetailPageClient id={id} />;
}
