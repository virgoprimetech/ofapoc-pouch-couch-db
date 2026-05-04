"use client";

import {useProperties} from "@/lib/db/use-properties";
import {type CreatePropertyInput, type PropertyStatus} from "@/lib/db/schema";
import {useAuthStore} from "@/features/auth/store";
import {useToast} from "@/hooks/use-toast";
import {ToastContainer} from "@/components/properties/toast-container";
import {CreatePropertyDialog} from "@/components/properties/create-property-dialog";
import {PropertyCard} from "@/components/properties/property-card";
import {Card, CardContent, CardHeader,} from "@/components/ui/card";
import {RiBuilding2Line,} from "@remixicon/react";

// --- Skeleton ---

function PropertyCardSkeleton() {
  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-start gap-3">
          <div className="size-9 rounded-sm bg-muted motion-safe:animate-pulse"/>
          <div className="space-y-2 flex-1">
            <div className="h-3.5 w-32 bg-muted motion-safe:animate-pulse rounded"/>
            <div className="h-3 w-48 bg-muted motion-safe:animate-pulse rounded"/>
          </div>
          <div className="h-5 w-14 bg-muted motion-safe:animate-pulse rounded"/>
        </div>
      </CardHeader>
      <CardContent className="space-y-2 pb-3">
        <div className="h-3 w-full bg-muted motion-safe:animate-pulse rounded"/>
        <div className="h-3 w-2/3 bg-muted motion-safe:animate-pulse rounded"/>
      </CardContent>
    </Card>
  );
}

// --- Main Page ---

export default function PropertyClient() {
  const user = useAuthStore((state) => state.user);
  const tenantId = user?.tenantId;
  const {properties, isLoading, error, create, update, softDelete} = useProperties(tenantId);
  const {toasts, addToast} = useToast();

  async function handleCreate(data: CreatePropertyInput) {
    if (!user) {
      throw new Error("Missing authenticated user identity for property creation");
    }

    const result = await create(data, {
      userId: user.userId,
      username: user.username,
      tenantId: user.tenantId,
      tenantCode: user.tenantCode,
    });
    if (result.success) addToast("Property created", "success");
    else throw new Error(result.error.message);
  }

  async function handleStatusChange(id: string, status: PropertyStatus) {
    const result = await update(id, {status});
    if (result.success) addToast(`Status changed to ${status}`, "success");
    else addToast("message" in result.error ? result.error.message : "Failed to update status", "error");
  }

  async function handleDelete(id: string) {
    const result = await softDelete(id);
    if (result.success) addToast("Property deleted", "success");
    else addToast("message" in result.error ? result.error.message : "Failed to delete", "error");
  }

  const activeCount = properties.filter((p) => p.status === "ACTIVE").length;
  const draftCount = properties.filter((p) => p.status === "DRAFT").length;

  return (
    <div className="p-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Properties</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {properties.length === 0
              ? "Manage your properties offline"
              : `${properties.length} total — ${activeCount} active, ${draftCount} draft`}
          </p>
        </div>
        <CreatePropertyDialog onCreate={handleCreate}/>
      </div>

      {error && (
        <div
          className="mb-6 rounded-sm border border-destructive/20 bg-destructive/10 px-4 py-3 text-sm text-destructive"
          role="alert">
          {error}
        </div>
      )}

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({length: 3}, (_, i) => <PropertyCardSkeleton key={i}/>)}
        </div>
      ) : properties.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <div className="size-14 mb-4 rounded-full bg-muted flex items-center justify-center">
            <RiBuilding2Line className="size-6 text-muted-foreground" aria-hidden="true"/>
          </div>
          <p className="text-sm font-medium text-foreground mb-1">No properties yet</p>
          <p className="text-sm text-muted-foreground max-w-xs mb-4">
            Create your first property. Data is stored locally and synced when online.
          </p>
          <CreatePropertyDialog onCreate={handleCreate}/>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {properties.map((property, index) => (
            <div
              key={property._id}
              className="motion-safe:animate-[fade-in_200ms_ease-out_both]"
              style={{animationDelay: `${index * 50}ms`}}
            >
              <PropertyCard
                property={property}
                onStatusChange={handleStatusChange}
                onDelete={handleDelete}
              />
            </div>
          ))}
        </div>
      )}

      <ToastContainer toasts={toasts}/>
    </div>
  );
}
