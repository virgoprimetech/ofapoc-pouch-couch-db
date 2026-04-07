package com.primetech.poc.identity.features.auth.refresh;

import java.util.List;
import java.util.UUID;

/**
 * Refresh Response
 */
public record RefreshResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UUID tenantId,
    UUID userId,
    String email,
    List<String> roles
) {
  public RefreshResponse {
    tokenType = "Bearer";
  }
}
