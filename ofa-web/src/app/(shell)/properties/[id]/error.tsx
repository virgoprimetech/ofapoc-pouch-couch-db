"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { RiArrowLeftLine, RiRefreshLine } from "@remixicon/react";

export default function PropertyError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <div className="size-14 mb-4 rounded-full bg-destructive/10 flex items-center justify-center">
        <RiRefreshLine className="size-6 text-destructive" aria-hidden="true" />
      </div>
      <h2 className="text-lg font-semibold mb-1">Something went wrong</h2>
      <p className="text-sm text-muted-foreground mb-4 max-w-sm">
        {error.message || "An unexpected error occurred while loading this property."}
      </p>
      <div className="flex gap-2">
        <Button onClick={reset} className="gap-1.5 cursor-pointer">
          <RiRefreshLine className="size-4" />
          Try Again
        </Button>
        <Button asChild variant="outline" className="gap-1.5 cursor-pointer">
          <Link href="/">
            <RiArrowLeftLine className="size-4" />
            Back to Properties
          </Link>
        </Button>
      </div>
    </div>
  );
}
