package com.primetech.poc.identity.features.tenant.create;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create Tenant Request
 */
public record CreateTenantRequest(
    @NotBlank(message = "Tenant code is required")
    @Size(min = 2, max = 50, message = "Tenant code must be between 2 and 50 characters")
    @Pattern(regexp = "^[a-z][a-z0-9_-]*$", message = "Tenant code must start with a letter and contain only lowercase letters, numbers, underscores, and hyphens")
    String code,

    @NotBlank(message = "Display name is required")
    @Size(max = 255, message = "Display name must be at most 255 characters")
    String displayName,

    @NotBlank(message = "Admin username is required")
    @Size(min = 3, max = 50, message = "Admin username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Admin username can only contain letters, numbers, underscores, and hyphens")
    String adminUsername,

    @NotBlank(message = "Admin email is required")
    @Email(message = "Admin email must be valid")
    String adminEmail,

    @NotBlank(message = "Admin password is required")
    @Size(min = 8, max = 100, message = "Admin password must be between 8 and 100 characters")
    String adminPassword
) {
}
