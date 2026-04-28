"use client";

import { useState } from "react";
import type { PropertyDoc, CreatePropertyInput } from "@/lib/db/schema";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PropertyForm } from "./property-form";

export function EditPropertyDialog({
  property,
  open,
  onOpenChange,
  onSave,
}: {
  property: PropertyDoc;
  open: boolean;
  onOpenChange: (v: boolean) => void;
  onSave: (data: CreatePropertyInput) => Promise<void>;
}) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(data: CreatePropertyInput) {
    setIsSubmitting(true);
    setError(null);
    try {
      await onSave(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save changes");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Edit Property</DialogTitle>
          <DialogDescription>Update property details.</DialogDescription>
        </DialogHeader>
        <PropertyForm
          initialData={property}
          onSubmit={handleSubmit}
          isSubmitting={isSubmitting}
          error={error}
        />
        <DialogFooter className="gap-2">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} className="cursor-pointer">
            Cancel
          </Button>
          <Button
            type="submit"
            form={`property-edit-form-${property._id}`}
            disabled={isSubmitting}
            className="cursor-pointer"
          >
            {isSubmitting ? "Saving..." : "Save Changes"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
