package com.primetech.poc.identity.features.auth.refresh;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh Request
 */
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {
}
