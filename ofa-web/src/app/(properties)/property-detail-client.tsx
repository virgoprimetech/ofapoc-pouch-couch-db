"use client";

import { useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useProperty } from "@/lib/db/use-property";
import { useOnline } from "@/lib/db/use-properties";
import { type CreatePropertyInput } from "@/lib/db/schema";
import { STATUS_STYLES, TYPE_LABELS } from "@/lib/constants";
import { useToast } from "@/hooks/use-toast";
import { ToastContainer } from "@/components/properties/toast-container";
import { EditPropertyDialog } from "@/components/properties/edit-property-dialog";
import { ConfirmDeleteDialog } from "@/components/properties/confirm-delete-dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import {
  RiArrowLeftLine,
  RiEditLine,
  RiDeleteBinLine,
  RiHotelLine,
  RiMapPin2Line,
  RiPhoneLine,
  RiMailLine,
  RiGlobalLine,
  RiTimeLine,
  RiMoneyDollarCircleLine,
  RiDoorLine,
  RiWifiOffLine,
  RiCalendarLine,
  RiHashtag,
} from "@remixicon/react";

function DetailField({ label, value, icon }: { label: string; value?: string | null; icon?: React.ReactNode }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-xs text-muted-foreground flex items-center gap-1">
        {icon}
        {label}
      </dt>
      <dd className="text-sm font-medium truncate">
        {value || <span className="text-muted-foreground italic">Not set</span>}
      </dd>
    </div>
  );
}

export default function PropertyDetailClient({ id }: { id: string }) {
  const router = useRouter();
  const isOnline = useOnline();
  const { property, isLoading, error, update, softDelete } = useProperty(id);
  const { toasts, addToast } = useToast();

  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const handleSave = useCallback(
    async (data: CreatePropertyInput) => {
      const result = await update(data);
      if (result.success) {
        addToast(isOnline ? "Property updated" : "Saved locally", "success");
        setEditOpen(false);
      } else {
        const msg = "message" in result.error ? result.error.message : "Failed to update";
        addToast(msg, "error");
      }
    },
    [update, addToast, isOnline],
  );

  const handleDelete = useCallback(async () => {
    setIsDeleting(true);
    const result = await softDelete();
    if (result.success) {
      addToast("Property deleted", "success");
      router.push("/");
    } else {
      const msg = "message" in result.error ? result.error.message : "Failed to delete";
      addToast(msg, "error");
    }
    setIsDeleting(false);
    setDeleteOpen(false);
  }, [softDelete, router, addToast]);

  if (isLoading) {
    return (
      <div className="p-6 animate-pulse space-y-4 max-w-3xl">
        <div className="h-8 w-32 bg-muted rounded" />
        <div className="h-40 bg-muted rounded" />
      </div>
    );
  }

  if (error || !property) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <div className="size-14 mb-4 rounded-full bg-muted flex items-center justify-center">
          <RiHotelLine className="size-6 text-muted-foreground" aria-hidden="true" />
        </div>
        <h2 className="text-lg font-semibold mb-1">Property Not Found</h2>
        <p className="text-sm text-muted-foreground mb-4">{error || "This property does not exist."}</p>
        <Button asChild variant="outline" className="gap-1.5 cursor-pointer">
          <button onClick={() => router.push("/")}>
            <RiArrowLeftLine className="size-4" />
            Back to Properties
          </button>
        </Button>
      </div>
    );
  }

  const statusStyle = STATUS_STYLES[property.status];
  const fullAddress = [
    property.address_line1,
    property.address_line2,
    property.city,
    property.state_province,
    property.postal_code,
    property.country,
  ]
    .filter(Boolean)
    .join(", ");

  const formatDate = (iso: string) => {
    try {
      return new Date(iso).toLocaleString();
    } catch {
      return iso;
    }
  };

  return (
    <div className="p-6 max-w-3xl">
      {/* Offline banner */}
      {!isOnline && (
        <div className="mb-4 flex items-center gap-2 rounded-sm border border-amber-300/30 bg-amber-50 dark:bg-amber-950/20 px-3 py-2 text-xs text-amber-700 dark:text-amber-400">
          <RiWifiOffLine className="size-3.5 shrink-0" />
          Viewing offline — changes save locally
        </div>
      )}

      {/* Header */}
      <div className="mb-6 flex items-center gap-3">
        <Button variant="ghost" size="icon-sm" onClick={() => router.back()} className="cursor-pointer" aria-label="Back">
          <RiArrowLeftLine className="size-4" />
        </Button>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3">
            <div className="flex size-9 shrink-0 items-center justify-center rounded-sm bg-primary/10 text-primary">
              <RiHotelLine className="size-4" />
            </div>
            <div className="min-w-0">
              <h1 className="text-lg font-semibold truncate">{property.name}</h1>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span>{TYPE_LABELS[property.property_type]}</span>
                <span>·</span>
                <Badge variant={statusStyle.variant} className="text-[10px]">{statusStyle.label}</Badge>
              </div>
            </div>
          </div>
        </div>
        <div className="flex gap-2 shrink-0">
          <Button variant="outline" size="sm" onClick={() => setEditOpen(true)} className="gap-1.5 cursor-pointer">
            <RiEditLine className="size-3.5" />
            Edit
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setDeleteOpen(true)}
            disabled={property.status === "ARCHIVED"}
            className="gap-1.5 text-destructive hover:text-destructive cursor-pointer"
          >
            <RiDeleteBinLine className="size-3.5" />
            Delete
          </Button>
        </div>
      </div>

      <div className="space-y-4">
        {/* Description */}
        {property.description && (
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">Description</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground whitespace-pre-wrap">{property.description}</p>
            </CardContent>
          </Card>
        )}

        {/* Address */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm flex items-center gap-1.5">
              <RiMapPin2Line className="size-3.5" /> Address
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
              <DetailField label="Address Line 1" value={property.address_line1} />
              <DetailField label="Address Line 2" value={property.address_line2} />
              <DetailField label="City" value={property.city} />
              <DetailField label="State / Province" value={property.state_province} />
              <DetailField label="Postal Code" value={property.postal_code} />
              <DetailField label="Country" value={property.country} />
              <DetailField
                label="Coordinates"
                value={
                  property.latitude != null && property.longitude != null
                    ? `${property.latitude}, ${property.longitude}`
                    : null
                }
              />
            </dl>
            {fullAddress && (
              <>
                <Separator className="my-3" />
                <p className="text-xs text-muted-foreground">{fullAddress}</p>
              </>
            )}
          </CardContent>
        </Card>

        {/* Contact */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Contact</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
              <DetailField label="Phone" value={property.phone} icon={<RiPhoneLine className="size-3" />} />
              <DetailField label="Email" value={property.email} icon={<RiMailLine className="size-3" />} />
              <DetailField label="Website" value={property.website} icon={<RiGlobalLine className="size-3" />} />
            </dl>
          </CardContent>
        </Card>

        {/* Operational */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Operational</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
              <DetailField
                label="Total Rooms"
                value={property.total_rooms != null ? String(property.total_rooms) : null}
                icon={<RiDoorLine className="size-3" />}
              />
              <DetailField
                label="Currency"
                value={property.currency}
                icon={<RiMoneyDollarCircleLine className="size-3" />}
              />
              <DetailField
                label="Timezone"
                value={property.timezone}
                icon={<RiTimeLine className="size-3" />}
              />
            </dl>
          </CardContent>
        </Card>

        {/* Audit */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm flex items-center gap-1.5">
              <RiCalendarLine className="size-3.5" /> Audit
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
              <DetailField label="Created" value={formatDate(property.created_at)} icon={<RiCalendarLine className="size-3" />} />
              <DetailField label="Updated" value={formatDate(property.updated_at)} icon={<RiCalendarLine className="size-3" />} />
              <DetailField label="Tenant" value={property.tenant_id} icon={<RiHashtag className="size-3" />} />
              <DetailField label="Document ID" value={property._id} icon={<RiHashtag className="size-3" />} />
            </dl>
          </CardContent>
        </Card>
      </div>

      {/* Dialogs */}
      {property && (
        <EditPropertyDialog
          key={property._id}
          property={property}
          open={editOpen}
          onOpenChange={setEditOpen}
          onSave={handleSave}
        />
      )}
      <ConfirmDeleteDialog
        propertyName={property.name}
        onConfirm={handleDelete}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        isDeleting={isDeleting}
      />

      <ToastContainer toasts={toasts} />
    </div>
  );
}
