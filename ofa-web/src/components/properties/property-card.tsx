"use client";

import { useState } from "react";
import { PROPERTY_STATUSES, type PropertyStatus, type PropertyDoc } from "@/lib/db/schema";
import { STATUS_STYLES, TYPE_LABELS } from "@/lib/constants";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  RiHotelLine,
  RiDeleteBinLine,
  RiArrowDownLine,
  RiMapPin2Line,
  RiTimeLine,
  RiMoneyDollarCircleLine,
  RiDoorLine,
} from "@remixicon/react";
import Link from "next/link";
import { ConfirmDeleteDialog } from "./confirm-delete-dialog";

export function PropertyCard({
  property,
  onStatusChange,
  onDelete,
}: {
  property: PropertyDoc;
  onStatusChange: (id: string, s: PropertyStatus) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}) {
  const [isUpdating, setIsUpdating] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const statusStyle = STATUS_STYLES[property.status];

  async function handleStatusChange(newStatus: string) {
    setIsUpdating(true);
    try {
      await onStatusChange(property._id, newStatus as PropertyStatus);
    } finally {
      setIsUpdating(false);
    }
  }

  async function handleConfirmDelete() {
    setIsDeleting(true);
    try {
      await onDelete(property._id);
      setDeleteOpen(false);
    } finally {
      setIsDeleting(false);
    }
  }

  const location = [property.city, property.country].filter(Boolean).join(", ");

  return (
    <>
      <Card className="group transition-shadow hover:shadow-md">
        {/* Clickable header + content → navigates to detail */}
        <Link href={`/properties/${property._id.replace("property::", "")}`} className="block">
          <CardHeader className="pb-3">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-start gap-3 min-w-0">
                <div className="flex size-9 shrink-0 items-center justify-center rounded-sm bg-primary/10 text-primary">
                  <RiHotelLine className="size-4" />
                </div>
                <div className="min-w-0 space-y-0.5">
                  <CardTitle className="truncate text-sm">{property.name}</CardTitle>
                  <div className="flex items-center gap-1 text-xs text-muted-foreground">
                    <span>{TYPE_LABELS[property.property_type]}</span>
                    {location && (
                      <>
                        <span>·</span>
                        <RiMapPin2Line className="size-3" />
                        <span>{location}</span>
                      </>
                    )}
                  </div>
                </div>
              </div>
              <Badge variant={statusStyle.variant} className="shrink-0 text-[10px]">
                {statusStyle.label}
              </Badge>
            </div>
          </CardHeader>

          <CardContent className="space-y-2 pb-3">
            {property.description && (
              <p className="text-xs text-muted-foreground line-clamp-2">{property.description}</p>
            )}
            <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
              {property.total_rooms != null && (
                <span className="flex items-center gap-1">
                  <RiDoorLine className="size-3" />
                  {property.total_rooms} rooms
                </span>
              )}
              {property.currency && (
                <span className="flex items-center gap-1">
                  <RiMoneyDollarCircleLine className="size-3" />
                  {property.currency}
                </span>
              )}
              {property.timezone && (
                <span className="flex items-center gap-1">
                  <RiTimeLine className="size-3" />
                  {property.timezone}
                </span>
              )}
            </div>
          </CardContent>
        </Link>

        {/* Footer with actions — outside the Link */}
        <CardFooter className="gap-2 pt-0 border-t">
          <Select value={property.status} onValueChange={handleStatusChange} disabled={isUpdating}>
            <SelectTrigger size="sm" className="w-32 cursor-pointer">
              <RiArrowDownLine className="size-3 mr-1" />
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PROPERTY_STATUSES.map((s) => (
                <SelectItem key={s} value={s} className="cursor-pointer">
                  {STATUS_STYLES[s].label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setDeleteOpen(true)}
            disabled={isUpdating || property.status === "ARCHIVED"}
            className="gap-1 text-destructive hover:text-destructive cursor-pointer"
          >
            <RiDeleteBinLine className="size-3.5" />
            <span className="text-xs">Delete</span>
          </Button>
        </CardFooter>
      </Card>
      <ConfirmDeleteDialog
        propertyName={property.name}
        onConfirm={handleConfirmDelete}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        isDeleting={isDeleting}
      />
    </>
  );
}
