package com.primetech.poc.identity.shared.oauth2;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * RFC 7662 OAuth 2.0 Token Introspection Response.
 * <p>
 * Response structure for the token introspection endpoint.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7662">RFC 7662</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntrospectionResponse(
    boolean active,
    String token_type,
    Long exp,
    Long iat,
    String sub,
    String username,
    String email,
    List<String> scope,
    String tenant_id,
    String tenant_code,
    List<String> roles
) {

  /**
   * Create an inactive token response.
   */
  public static IntrospectionResponse inactive() {
    return new IntrospectionResponse(false, null, null, null, null, null, null, null, null, null, null);
  }

  /**
   * Create an active access token response.
   */
  public static IntrospectionResponse accessToken(
      Instant expiresAt,
      Instant issuedAt,
      String subject,
      String username,
      String email,
      String tenantId,
      String tenantCode,
      List<String> roles
  ) {
    return new IntrospectionResponse(
        true,
        "access_token",
        expiresAt != null ? expiresAt.getEpochSecond() : null,
        issuedAt != null ? issuedAt.getEpochSecond() : null,
        subject,
        username,
        email,
        roles != null ? roles : List.of(),
        tenantId,
        tenantCode,
        roles
    );
  }

  /**
   * Create an active refresh token response.
   */
  public static IntrospectionResponse refreshToken(
      Instant expiresAt,
      Instant issuedAt,
      String subject,
      String tenantId,
      String tenantCode
  ) {
    return new IntrospectionResponse(
        true,
        "refresh_token",
        expiresAt != null ? expiresAt.getEpochSecond() : null,
        issuedAt != null ? issuedAt.getEpochSecond() : null,
        subject,
        null,
        null,
        null,
        tenantId,
        tenantCode,
        null
    );
  }
}
