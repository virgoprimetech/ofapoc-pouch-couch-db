import { z } from "zod";

// --- Enums ---

export const PROPERTY_TYPES = [
  "HOTEL",
  "RESORT",
  "MOTEL",
  "BNB",
  "VACATION_RENTAL",
  "HOSTEL",
  "GUESTHOUSE",
  "APARTMENT_HOTEL",
  "BOUTIQUE_HOTEL",
  "SERVICED_APARTMENT",
  "OTHER",
] as const;

export const PropertyTypeSchema = z.enum(PROPERTY_TYPES);
export type PropertyType = z.infer<typeof PropertyTypeSchema>;

export const PROPERTY_STATUSES = [
  "DRAFT",
  "ACTIVE",
  "SUSPENDED",
  "CLOSED",
  "ARCHIVED",
] as const;

export const PropertyStatusSchema = z.enum(PROPERTY_STATUSES);
export type PropertyStatus = z.infer<typeof PropertyStatusSchema>;

// --- Valid status transitions ---
const ALLOWED_TRANSITIONS: Record<PropertyStatus, PropertyStatus[]> = {
  DRAFT: ["ACTIVE", "ARCHIVED"],
  ACTIVE: ["SUSPENDED", "CLOSED"],
  SUSPENDED: ["ACTIVE", "CLOSED"],
  CLOSED: ["ARCHIVED"],
  ARCHIVED: [],
};

export function canTransitionTo(
  from: PropertyStatus,
  to: PropertyStatus,
): boolean {
  return ALLOWED_TRANSITIONS[from]?.includes(to) ?? false;
}

// Fields required to transition DRAFT → ACTIVE
const ACTIVE_REQUIRED_FIELDS = [
  "name",
  "city",
  "country",
  "currency",
  "timezone",
  "total_rooms",
] as const;

export function validateActivation(
  doc: Record<string, unknown>,
): { valid: true } | { valid: false; missing: string[] } {
  const missing = ACTIVE_REQUIRED_FIELDS.filter(
    (field) => doc[field] === undefined || doc[field] === null || doc[field] === "",
  );
  return missing.length > 0 ? { valid: false, missing } : { valid: true };
}

// --- Document type discriminator ---
export type DocType = "property" | "room" | "reservation" | "guest" | "rate_plan";

/** Extract the type prefix from a document _id */
export function docTypeFromId(id: string): string {
  const sep = id.indexOf("::");
  return sep > 0 ? id.slice(0, sep) : "unknown";
}

// --- PouchDB Document Schema ---

export const PropertyDocSchema = z.object({
  _id: z.string(),
  _rev: z.string().optional(),
  type: z.literal("property"),

  // Identity
  user_id: z.string().min(1, "User ID is required"),
  username: z.string().min(1, "Username is required"),
  tenant_id: z.string().min(1, "Tenant ID is required"),
  tenant_code: z.string().min(1, "Tenant code is required"),

  // Core
  name: z.string().min(1, "Property name is required"),
  property_type: PropertyTypeSchema,
  status: PropertyStatusSchema.default("DRAFT"),
  description: z.string().optional().default(""),

  // Address
  address_line1: z.string().optional().default(""),
  address_line2: z.string().optional().default(""),
  city: z.string().optional().default(""),
  state_province: z.string().optional().default(""),
  postal_code: z.string().optional().default(""),
  country: z.string().optional().default(""),

  // Geolocation
  latitude: z.number().optional(),
  longitude: z.number().optional(),

  // Contact
  phone: z.string().optional().default(""),
  email: z.string().email().optional().or(z.literal("")),
  website: z.string().url().optional().or(z.literal("")),

  // Operational
  timezone: z.string().optional().default(""),
  currency: z.string().optional().default(""),
  total_rooms: z.number().int().min(0).optional(),

  // Audit
  created_at: z.string(),
  updated_at: z.string(),
  updated_by: z.string().optional().default("system"),
  deleted_at: z.string().nullable().optional(),
});

export type PropertyDoc = z.infer<typeof PropertyDocSchema>;

// --- Form input schema (no auto-generated / system fields) ---

export const CreatePropertyInputSchema = z.object({
  name: z.string().min(1, "Property name is required"),
  property_type: PropertyTypeSchema.default("HOTEL"),
  description: z.string().optional().default(""),
  address_line1: z.string().optional().default(""),
  address_line2: z.string().optional().default(""),
  city: z.string().optional().default(""),
  state_province: z.string().optional().default(""),
  postal_code: z.string().optional().default(""),
  country: z.string().optional().default(""),
  latitude: z.number().optional(),
  longitude: z.number().optional(),
  phone: z.string().optional().default(""),
  email: z.string().email().optional().or(z.literal("")),
  website: z.string().url().optional().or(z.literal("")),
  timezone: z.string().optional().default(""),
  currency: z.string().optional().default(""),
  total_rooms: z.number().int().min(0).optional(),
});

export type CreatePropertyInput = z.infer<typeof CreatePropertyInputSchema>;

export const UpdatePropertyInputSchema = CreatePropertyInputSchema.partial();
export type UpdatePropertyInput = z.infer<typeof UpdatePropertyInputSchema>;

// --- Helpers ---

/** Generate a PouchDB _id with the property:: prefix */
export function propertyId(uuid?: string): string {
  return `property::${uuid ?? crypto.randomUUID()}`;
}

/** Create a full PropertyDoc from form input */
export interface PropertyIdentity {
  userId: string;
  username: string;
  tenantId: string;
  tenantCode: string;
}

export function createPropertyDoc(
  input: CreatePropertyInput,
  identity: PropertyIdentity,
): Omit<PropertyDoc, "_rev"> {
  const now = new Date().toISOString();
  return {
    _id: propertyId(),
    type: "property",
    user_id: identity.userId,
    username: identity.username,
    tenant_id: identity.tenantId,
    tenant_code: identity.tenantCode,
    status: "DRAFT",
    ...input,
    created_at: now,
    updated_at: now,
    updated_by: identity.username,
    deleted_at: null,
  };
}
