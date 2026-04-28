"use client";

import { useState } from "react";
import type { CreatePropertyInput } from "@/lib/db/schema";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { RiAddLine } from "@remixicon/react";
import { PropertyForm } from "./property-form";

export function CreatePropertyDialog({
  onCreate,
}: {
  onCreate: (data: CreatePropertyInput) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(data: CreatePropertyInput) {
    setIsSubmitting(true);
    setError(null);
    try {
      await onCreate(data);
      setOpen(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create property");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button className="gap-1.5 cursor-pointer">
          <RiAddLine className="size-4" />
          Add Property
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Create Property</DialogTitle>
          <DialogDescription>Add a new property. It will be created in DRAFT status.</DialogDescription>
        </DialogHeader>
        <PropertyForm onSubmit={handleSubmit} isSubmitting={isSubmitting} error={error} />
        <DialogFooter className="gap-2">
          <Button type="button" variant="outline" onClick={() => setOpen(false)} className="cursor-pointer">
            Cancel
          </Button>
          <Button
            type="submit"
            form="property-create-form"
            disabled={isSubmitting}
            className="cursor-pointer"
          >
            {isSubmitting ? "Creating..." : "Create Property"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
