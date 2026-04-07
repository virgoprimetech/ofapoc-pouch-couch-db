package com.primetech.poc.identity.features.role.update;

import com.primetech.poc.identity.features.role.domain.RoleScope;

import java.util.Set;
import java.util.UUID;

/**
 * Update Role Request
 */
public record UpdateRoleRequest(
    String displayName,
    RoleScope scope,
    String description,
    Set<UUID> permissionIds
) {
}
