"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Button } from "@/components/ui/button";
import { RiArrowLeftLine, RiQuestionLine } from "@remixicon/react";
import PropertyDetailClient from "@/app/(properties)/property-detail-client";

const PropertyClient = dynamic(
  () => import("../(properties)/property-client"),
  { ssr: false },
);

const ConflictClient = dynamic(
  () => import("../(conflicts)/conflict-client"),
  { ssr: false },
);

function ShellPageNotFound() {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <div className="size-14 mb-4 rounded-full bg-muted flex items-center justify-center">
        <RiQuestionLine className="size-6 text-muted-foreground" aria-hidden="true" />
      </div>
      <h2 className="text-lg font-semibold mb-1">Page Not Found</h2>
      <p className="text-sm text-muted-foreground mb-4 max-w-sm">
        The page you requested does not exist in this shell view.
      </p>
      <Button asChild variant="outline" className="gap-1.5 cursor-pointer">
        <Link href="/properties">
          <RiArrowLeftLine className="size-4" />
          Back to Properties
        </Link>
      </Button>
    </div>
  );
}

export default function ShellClient() {
  const pathname = usePathname();

  console.log("Online First Auctions: ShellClient rendered with pathname:", pathname);
  if (pathname === "/conflicts") {
    return <ConflictClient />;
  }

  if (pathname === "/" || pathname === "/properties") {
    return <PropertyClient />;
  }

  const propertyDetailRegex = /^\/properties\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (propertyDetailRegex.test(pathname)) {
    return <PropertyDetailClient id={pathname.split("/").pop()!} />;
  }

  return <ShellPageNotFound />;
}
