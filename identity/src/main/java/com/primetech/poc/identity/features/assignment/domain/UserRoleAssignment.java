package com.primetech.poc.identity.features.assignment.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * UserRoleAssignment Domain Model
 * <p>
 * Represents a role assignment to a user with optional property/chain scope.
 */
public record UserRoleAssignment(
    UUID id,
    UUID userId,
    UUID roleId,
    UUID propertyId,
    UUID chainId,
    Instant validFrom,
    Instant validUntil,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {
  /**
   * Check if the assignment is soft-deleted
   */
  public boolean isDeleted() {
    return deletedAt != null;
  }

  /**
   * Check if the assignment is currently valid
   */
  public boolean isCurrentlyValid() {
    Instant now = Instant.now();
    if (now.isBefore(validFrom)) {
      return false;
    }
    return validUntil == null || !now.isAfter(validUntil);
  }

  /**
   * Create a new UserRoleAssignment with updated timestamp
   */
  public UserRoleAssignment withUpdatedAt(Instant newUpdatedAt) {
    return new UserRoleAssignment(
        id, userId, roleId, propertyId, chainId,
        validFrom, validUntil,
        createdAt, newUpdatedAt, deletedAt
    );
  }

  /**
   * Create a new UserRoleAssignment with deleted timestamp (soft delete)
   */
  public UserRoleAssignment withDeletedAt(Instant newDeletedAt) {
    return new UserRoleAssignment(
        id, userId, roleId, propertyId, chainId,
        validFrom, validUntil,
        createdAt, updatedAt, newDeletedAt
    );
  }
}
