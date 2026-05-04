"use client";

import { useState, type FormEvent } from "react";
import {
  PROPERTY_TYPES,
  type PropertyType,
  type PropertyDoc,
  type CreatePropertyInput,
} from "@/lib/db/schema";
import { TYPE_LABELS } from "@/lib/constants";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

interface PropertyFormProps {
  initialData?: PropertyDoc;
  onSubmit: (data: CreatePropertyInput) => Promise<void>;
  isSubmitting: boolean;
  error: string | null;
}

export function PropertyForm({ initialData, onSubmit, error }: PropertyFormProps) {
  const [name, setName] = useState(initialData?.name ?? "");
  const [propertyType, setPropertyType] = useState<PropertyType>(initialData?.property_type ?? "HOTEL");
  const [description, setDescription] = useState(initialData?.description ?? "");
  const [addressLine1, setAddressLine1] = useState(initialData?.address_line1 ?? "");
  const [addressLine2, setAddressLine2] = useState(initialData?.address_line2 ?? "");
  const [city, setCity] = useState(initialData?.city ?? "");
  const [stateProvince, setStateProvince] = useState(initialData?.state_province ?? "");
  const [postalCode, setPostalCode] = useState(initialData?.postal_code ?? "");
  const [country, setCountry] = useState(initialData?.country ?? "");
  const [latitude, setLatitude] = useState(initialData?.latitude?.toString() ?? "");
  const [longitude, setLongitude] = useState(initialData?.longitude?.toString() ?? "");
  const [phone, setPhone] = useState(initialData?.phone ?? "");
  const [email, setEmail] = useState(initialData?.email ?? "");
  const [website, setWebsite] = useState(initialData?.website ?? "");
  const [timezone, setTimezone] = useState(initialData?.timezone ?? "");
  const [currency, setCurrency] = useState(initialData?.currency ?? "");
  const [totalRooms, setTotalRooms] = useState(initialData?.total_rooms?.toString() ?? "");

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    await onSubmit({
      name: name.trim(),
      property_type: propertyType,
      description,
      address_line1: addressLine1,
      address_line2: addressLine2,
      city,
      state_province: stateProvince,
      postal_code: postalCode,
      country,
      latitude: latitude ? parseFloat(latitude) : undefined,
      longitude: longitude ? parseFloat(longitude) : undefined,
      phone,
      email,
      website,
      timezone,
      currency,
      total_rooms: totalRooms ? parseInt(totalRooms, 10) : undefined,
    });
  }

  return (
    <form id={initialData ? `property-edit-form-${initialData._id}` : "property-create-form"} onSubmit={handleSubmit} className="grid gap-4">
      {/* Core */}
      <div className="grid gap-2">
        <Label htmlFor="name">
          Name <span className="text-destructive">*</span>
        </Label>
        <Input
          id="name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Grand Hotel"
          autoFocus
        />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div className="grid gap-2">
          <Label>Type</Label>
          <Select value={propertyType} onValueChange={(v) => setPropertyType(v as PropertyType)}>
            <SelectTrigger className="w-full cursor-pointer">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PROPERTY_TYPES.map((type) => (
                <SelectItem key={type} value={type} className="cursor-pointer">
                  {TYPE_LABELS[type]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="totalRooms">Total Rooms</Label>
          <Input
            id="totalRooms"
            type="number"
            min="0"
            value={totalRooms}
            onChange={(e) => setTotalRooms(e.target.value)}
            placeholder="100"
          />
        </div>
      </div>
      <div className="grid gap-2">
        <Label htmlFor="description">Description</Label>
        <Textarea
          id="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="A luxury hotel in the heart of the city"
          rows={2}
        />
      </div>

      {/* Address */}
      <div className="grid gap-2">
        <Label htmlFor="addressLine1">Address Line 1</Label>
        <Input
          id="addressLine1"
          value={addressLine1}
          onChange={(e) => setAddressLine1(e.target.value)}
          placeholder="123 Main St"
        />
      </div>
      <div className="grid gap-2">
        <Label htmlFor="addressLine2">Address Line 2</Label>
        <Input
          id="addressLine2"
          value={addressLine2}
          onChange={(e) => setAddressLine2(e.target.value)}
          placeholder="Suite 400"
        />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div className="grid gap-2">
          <Label htmlFor="city">City</Label>
          <Input id="city" value={city} onChange={(e) => setCity(e.target.value)} placeholder="New York" />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="stateProvince">State / Province</Label>
          <Input
            id="stateProvince"
            value={stateProvince}
            onChange={(e) => setStateProvince(e.target.value)}
            placeholder="NY"
          />
        </div>
      </div>
      <div className="grid grid-cols-3 gap-4">
        <div className="grid gap-2">
          <Label htmlFor="postalCode">Postal Code</Label>
          <Input
            id="postalCode"
            value={postalCode}
            onChange={(e) => setPostalCode(e.target.value)}
            placeholder="10001"
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="country">Country</Label>
          <Input id="country" value={country} onChange={(e) => setCountry(e.target.value)} placeholder="US" />
        </div>
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div className="grid gap-2">
          <Label htmlFor="latitude">Latitude</Label>
          <Input
            id="latitude"
            type="number"
            step="any"
            value={latitude}
            onChange={(e) => setLatitude(e.target.value)}
            placeholder="40.7128"
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="longitude">Longitude</Label>
          <Input
            id="longitude"
            type="number"
            step="any"
            value={longitude}
            onChange={(e) => setLongitude(e.target.value)}
            placeholder="-74.0060"
          />
        </div>
      </div>

      {/* Contact */}
      <div className="grid grid-cols-2 gap-4">
        <div className="grid gap-2">
          <Label htmlFor="phone">Phone</Label>
          <Input id="phone" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+1-555-0100" />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="info@hotel.com"
          />
        </div>
      </div>
      <div className="grid gap-2">
        <Label htmlFor="website">Website</Label>
        <Input
          id="website"
          type="url"
          value={website}
          onChange={(e) => setWebsite(e.target.value)}
          placeholder="https://hotel.com"
        />
      </div>

      {/* Operational */}
      <div className="grid grid-cols-2 gap-4">
        <div className="grid gap-2">
          <Label htmlFor="timezone">Timezone</Label>
          <Input
            id="timezone"
            value={timezone}
            onChange={(e) => setTimezone(e.target.value)}
            placeholder="America/New_York"
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="currency">Currency</Label>
          <Input id="currency" value={currency} onChange={(e) => setCurrency(e.target.value)} placeholder="USD" />
        </div>
      </div>

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}
