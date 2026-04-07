package com.primetech.poc.identity.features.auth.login;

import java.util.List;
import java.util.UUID;

/**
 * Login Response
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UUID tenantId,
    UUID userId,
    String username,
    String email,
    List<String> roles
) {
  public LoginResponse {
    tokenType = "Bearer";
  }
}
