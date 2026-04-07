package com.primetech.poc.identity.shared.security;

import com.primetech.poc.identity.shared.config.JwtProperties;
import com.primetech.poc.identity.shared.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JWT Token Provider
 * <p>
 * Handles JWT token generation and parsing using RSA signing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {

  private final JwtProperties jwtProperties;
  private final JwtEncoder jwtEncoder;
  private final JwtDecoder jwtDecoder;

  /**
   * Generate an access token for a user
   */
  public String generateAccessToken(UUID userId, String username, String email, List<String> roles, List<String> propertyIds) {
    Instant now = Instant.now();
    Instant expiry = now.plusMillis(jwtProperties.getAccessTokenExpirationMs());

    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(jwtProperties.getIssuer())
        .subject(userId.toString())
        .audience(List.of(jwtProperties.getAudience()))
        .issuedAt(now)
        .expiresAt(expiry)
        .claim("tenant_id", TenantContext.getTenantId())
        .claim("tenant_code", TenantContext.getTenantCode())
        .claim("username", username)
        .claim("email", email)
        .claim("roles", roles)
        .claim("propertyIds", propertyIds)
        .build();

    try {
      return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    } catch (JwtEncodingException e) {
      log.error("Failed to generate access token: {}", e.getMessage());
      throw new RuntimeException("Failed to generate access token", e);
    }
  }

  /**
   * Generate a refresh token for a user
   */
  public String generateRefreshToken(UUID userId) {
    Instant now = Instant.now();
    Instant expiry = now.plusMillis(jwtProperties.getRefreshTokenExpirationMs());

    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(jwtProperties.getIssuer())
        .subject(userId.toString())
        .audience(List.of(jwtProperties.getAudience()))
        .issuedAt(now)
        .expiresAt(expiry)
        .claim("type", "refresh")
        .claim("tenant_id", TenantContext.getTenantId())
        .claim("tenant_code", TenantContext.getTenantCode())
        .build();

    try {
      return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(claims)).getTokenValue();
    } catch (JwtEncodingException e) {
      log.error("Failed to generate refresh token: {}", e.getMessage());
      throw new RuntimeException("Failed to generate refresh token", e);
    }
  }

  /**
   * Validate a JWT token
   */
  public boolean validateToken(String token) {
    try {
      jwtDecoder.decode(token);
      return true;
    } catch (JwtValidationException ex) {
      log.error("Invalid JWT token: {}", ex.getMessage());
      return false;
    }
  }

  /**
   * Get JWT from token string
   */
  public Jwt getJwt(String token) {
    return jwtDecoder.decode(token);
  }

  /**
   * Get user ID from a JWT token
   */
  public UUID getUserId(String token) {
    Jwt jwt = getJwt(token);
    return UUID.fromString(jwt.getSubject());
  }

  /**
   * Get tenant code from a JWT token
   */
  public String getTenantCode(String token) {
    Jwt jwt = getJwt(token);
    return jwt.getClaimAsString("tenant_code");
  }

  /**
   * Get username from a JWT token
   */
  public String getUsername(String token) {
    Jwt jwt = getJwt(token);
    return jwt.getClaimAsString("username");
  }

  /**
   * Get email from a JWT token
   */
  public String getEmail(String token) {
    Jwt jwt = getJwt(token);
    return jwt.getClaimAsString("email");
  }

  /**
   * Get roles from a JWT token
   */
  @SuppressWarnings("unchecked")
  public List<String> getRoles(String token) {
    Jwt jwt = getJwt(token);
    List<String> roles = jwt.getClaimAsStringList("roles");
    return roles != null ? roles : List.of();
  }

  /**
   * Check if token is a refresh token
   */
  public boolean isRefreshToken(String token) {
    Jwt jwt = getJwt(token);
    return "refresh".equals(jwt.getClaimAsString("type"));
  }

  /**
   * Get expiration time in milliseconds
   */
  public long getAccessTokenExpirationMs() {
    return jwtProperties.getAccessTokenExpirationMs();
  }
}
