package com.primetech.poc.ofa_api.features.property.api;

import com.primetech.poc.ofa_api.features.property.domain.Property;
import com.primetech.poc.ofa_api.features.property.domain.PropertyStatus;
import com.primetech.poc.ofa_api.features.property.domain.PropertyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PropertyResponse(
    UUID id,
    String name,
    PropertyType propertyType,
    PropertyStatus status,
    String description,
    String addressLine1,
    String addressLine2,
    String city,
    String stateProvince,
    String postalCode,
    String country,
    BigDecimal latitude,
    BigDecimal longitude,
    String phone,
    String email,
    String website,
    String timezone,
    String currency,
    Integer totalRooms,
    String tenantId,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {
  public static PropertyResponse from(Property p) {
    return new PropertyResponse(
        p.getId(),
        p.getName(),
        p.getPropertyType(),
        p.getStatus(),
        p.getDescription(),
        p.getAddressLine1(),
        p.getAddressLine2(),
        p.getCity(),
        p.getStateProvince(),
        p.getPostalCode(),
        p.getCountry(),
        p.getLatitude(),
        p.getLongitude(),
        p.getPhone(),
        p.getEmail(),
        p.getWebsite(),
        p.getTimezone(),
        p.getCurrency(),
        p.getTotalRooms(),
        p.getTenantId(),
        p.getCreatedAt(),
        p.getUpdatedAt(),
        p.getDeletedAt()
    );
  }
}
