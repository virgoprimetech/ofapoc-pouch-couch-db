package com.primetech.poc.identity.features.assignment.assign;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Assign Role Request
 */
public record AssignRoleRequest(
    @NotNull(message = "User ID is required")
    UUID userId,

    @NotNull(message = "Role ID is required")
    UUID roleId,

    UUID propertyId,
    UUID chainId,
    Instant validFrom,
    Instant validUntil
) {
}
