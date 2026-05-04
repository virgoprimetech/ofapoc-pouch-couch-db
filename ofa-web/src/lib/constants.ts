import type { PropertyStatus, PropertyType } from "@/lib/db/schema";

export const STATUS_STYLES: Record<
  PropertyStatus,
  { variant: "default" | "secondary" | "destructive" | "outline"; label: string }
> = {
  DRAFT: { variant: "secondary", label: "Draft" },
  ACTIVE: { variant: "default", label: "Active" },
  SUSPENDED: { variant: "destructive", label: "Suspended" },
  CLOSED: { variant: "outline", label: "Closed" },
  ARCHIVED: { variant: "outline", label: "Archived" },
};

export const TYPE_LABELS: Record<PropertyType, string> = {
  HOTEL: "Hotel",
  RESORT: "Resort",
  MOTEL: "Motel",
  BNB: "B&B",
  VACATION_RENTAL: "Vacation Rental",
  HOSTEL: "Hostel",
  GUESTHOUSE: "Guesthouse",
  APARTMENT_HOTEL: "Apartment Hotel",
  BOUTIQUE_HOTEL: "Boutique Hotel",
  SERVICED_APARTMENT: "Serviced Apartment",
  OTHER: "Other",
};
