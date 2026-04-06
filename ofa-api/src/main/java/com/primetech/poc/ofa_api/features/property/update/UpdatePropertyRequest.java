package com.primetech.poc.ofa_api.features.property.update;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Update Property Request
 */
public record UpdatePropertyRequest(
    String name,
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
    UUID userId
) {
}
