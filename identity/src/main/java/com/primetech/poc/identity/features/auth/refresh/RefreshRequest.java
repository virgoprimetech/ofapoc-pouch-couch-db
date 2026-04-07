package com.primetech.poc.identity.features.auth.refresh;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh Request
 */
public record RefreshRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {
}
