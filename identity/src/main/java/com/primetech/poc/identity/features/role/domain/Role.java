package com.primetech.poc.identity.features.role.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Role Domain Model
 * <p>
 * Pure domain model without JPA annotations.
 * Represents a role with permissions.
 */
public record Role(
    UUID id,
    String code,
    String displayName,
    RoleScope scope,
    boolean isSystem,
    String description,
    Set<UUID> permissionIds,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {
  /**
   * Check if the role is soft-deleted
   */
  public boolean isDeleted() {
    return deletedAt != null;
  }

  /**
   * Check if the role can be deleted
   */
  public boolean canBeDeleted() {
    return !isSystem && !isDeleted();
  }

  /**
   * Create a new Role with updated timestamp
   */
  public Role withUpdatedAt(Instant newUpdatedAt) {
    return new Role(
        id, code, displayName, scope, isSystem, description,
        permissionIds, createdAt, newUpdatedAt, deletedAt
    );
  }

  /**
   * Create a new Role with deleted timestamp (soft delete)
   */
  public Role withDeletedAt(Instant newDeletedAt) {
    return new Role(
        id, code, displayName, scope, isSystem, description,
        permissionIds, createdAt, updatedAt, newDeletedAt
    );
  }
}
