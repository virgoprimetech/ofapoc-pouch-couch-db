package com.primetech.poc.identity.features.auth.login;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Login Request
 */
public record LoginRequest(
    @NotBlank(message = "Tenant is required")
    @Size(min = 2, max = 50, message = "Tenant code must be between 2 and 50 characters")
    @Pattern(regexp = "^[a-z][a-z0-9_-]*$", message = "Tenant code must start with a letter and contain only lowercase letters, numbers, underscores, and hyphens")
    String tenant,

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain letters, numbers, underscores, and hyphens")
    String username,

    @NotBlank(message = "Password is required")
    String password
) {
}
