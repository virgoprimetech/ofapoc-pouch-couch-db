package com.primetech.poc.identity.shared.oauth2;

import com.primetech.poc.identity.shared.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * RFC 7662 OAuth 2.0 Token Introspection Endpoint.
 * <p>
 * Allows resource servers to validate tokens and retrieve token metadata.
 * This endpoint is used by ofa-api to validate tokens without storing secrets.
 * <p>
 * Endpoint: POST /oauth2/introspect
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7662">RFC 7662</a>
 */
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
@Slf4j
public class IntrospectionEndpoint {

  private final JwtTokenProvider jwtTokenProvider;

  /**
   * Introspect a token to determine its validity and metadata.
   * <p>
   * Returns token information if valid, or { "active": false } if invalid.
   *
   * @param token         The token to introspect
   * @param tokenTypeHint Optional hint about token type (access_token or refresh_token)
   * @return Introspection response
   */
  @PostMapping("/introspect")
  public ResponseEntity<IntrospectionResponse> introspect(
      @RequestParam("token") String token,
      @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint) {

    log.debug("Introspecting token (hint: {})", tokenTypeHint);

    try {
      // Validate the token
      if (!jwtTokenProvider.validateToken(token)) {
        log.debug("Token validation failed");
        return ResponseEntity.ok(IntrospectionResponse.inactive());
      }

      // Decode and extract claims
      Jwt jwt = jwtTokenProvider.getJwt(token);
      String subject = jwt.getSubject();
      Instant expiresAt = jwt.getExpiresAt();
      Instant issuedAt = jwt.getIssuedAt();
      String tenantId = jwt.getClaimAsString("tenant_id");
      String tenantCode = jwt.getClaimAsString("tenant_code");
      String tokenType = jwt.getClaimAsString("type");

      // Check if this is a refresh token
      if ("refresh".equals(tokenType)) {
        log.debug("Token is a valid refresh token for subject: {}", subject);
        return ResponseEntity.ok(IntrospectionResponse.refreshToken(
            expiresAt, issuedAt, subject, tenantId, tenantCode
        ));
      }

      // Access token - extract additional claims
      String username = jwt.getClaimAsString("username");
      String email = jwt.getClaimAsString("email");
      List<String> roles = jwt.getClaimAsStringList("roles");

      log.debug("Token is a valid access token for subject: {}, username: {}", subject, username);
      return ResponseEntity.ok(IntrospectionResponse.accessToken(
          expiresAt, issuedAt, subject, username, email, tenantId, tenantCode, roles
      ));

    } catch (Exception e) {
      log.warn("Token introspection failed: {}", e.getMessage());
      return ResponseEntity.ok(IntrospectionResponse.inactive());
    }
  }
}
