package com.primetech.poc.identity.features.role.create;

import com.primetech.poc.identity.features.role.domain.RoleScope;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Role Response
 */
public record RoleResponse(
    UUID id,
    String code,
    String displayName,
    RoleScope scope,
    boolean isSystem,
    String description,
    Set<UUID> permissionIds,
    Instant createdAt,
    Instant updatedAt
) {
}
