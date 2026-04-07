package com.primetech.poc.identity.features.role.create;

import com.primetech.poc.identity.features.role.domain.RoleScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Create Role Command
 */
public record CreateRoleCommand(
    @NotBlank(message = "Code is required")
    String code,

    @NotBlank(message = "Display name is required")
    String displayName,

    @NotNull(message = "Scope is required")
    RoleScope scope,

    String description,

    Set<UUID> permissionIds
) {
}
