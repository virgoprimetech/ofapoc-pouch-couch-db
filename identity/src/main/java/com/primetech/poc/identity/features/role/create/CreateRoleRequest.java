package com.primetech.poc.identity.features.role.create;

import com.primetech.poc.identity.features.role.domain.RoleScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Create Role Request
 */
public record CreateRoleRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must be at most 50 characters")
    String code,

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must be at most 100 characters")
    String displayName,

    @NotNull(message = "Scope is required")
    RoleScope scope,

    String description,

    Set<UUID> permissionIds
) {
}
