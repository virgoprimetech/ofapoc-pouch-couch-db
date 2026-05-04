"use client";

import { Button } from "@/components/ui/button";
import { RiRefreshLine } from "@remixicon/react";

export default function LoginError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="flex min-h-dvh items-center justify-center p-4">
      <div className="text-center space-y-3">
        <div className="size-12 mx-auto rounded-full bg-destructive/10 flex items-center justify-center">
          <RiRefreshLine className="size-5 text-destructive" />
        </div>
        <h2 className="text-lg font-semibold">Something went wrong</h2>
        <p className="text-sm text-muted-foreground max-w-xs">
          {error.message || "An unexpected error occurred."}
        </p>
        <Button onClick={reset} variant="outline" className="gap-1.5 cursor-pointer">
          <RiRefreshLine className="size-4" />
          Try again
        </Button>
      </div>
    </div>
  );
}