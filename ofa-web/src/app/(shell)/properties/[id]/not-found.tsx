import Link from "next/link";
import { Button } from "@/components/ui/button";
import { RiArrowLeftLine, RiHotelLine } from "@remixicon/react";

export default function PropertyNotFound() {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <div className="size-14 mb-4 rounded-full bg-muted flex items-center justify-center">
        <RiHotelLine className="size-6 text-muted-foreground" aria-hidden="true" />
      </div>
      <h2 className="text-lg font-semibold mb-1">Property Not Found</h2>
      <p className="text-sm text-muted-foreground mb-4 max-w-sm">
        This property may have been deleted or doesn&apos;t exist in your local database.
      </p>
      <Button asChild variant="outline" className="gap-1.5 cursor-pointer">
        <Link href="/">
          <RiArrowLeftLine className="size-4" />
          Back to Properties
        </Link>
      </Button>
    </div>
  );
}
