package com.primetech.poc.identity.features.assignment.assign;

import java.time.Instant;
import java.util.UUID;

/**
 * Assignment Response
 */
public record AssignmentResponse(
    UUID id,
    UUID userId,
    UUID roleId,
    String roleCode,
    String roleDisplayName,
    UUID propertyId,
    UUID chainId,
    Instant validFrom,
    Instant validUntil,
    Instant createdAt,
    Instant updatedAt
) {
}
