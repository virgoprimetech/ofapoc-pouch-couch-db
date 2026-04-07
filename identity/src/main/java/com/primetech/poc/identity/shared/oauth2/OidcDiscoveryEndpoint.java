package com.primetech.poc.identity.shared.oauth2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * OpenID Connect Discovery Endpoint.
 * <p>
 * Provides auto-configuration information for OAuth2/OIDC clients.
 * Follows OpenID Connect Discovery 1.0 specification.
 * <p>
 * Endpoint: GET /.well-known/openid-configuration
 *
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect Discovery 1.0</a>
 */
@RestController
public class OidcDiscoveryEndpoint {

  private final String issuerUri;

  public OidcDiscoveryEndpoint(
      @Value("${identity.oauth2.issuer-uri}") String issuerUri) {
    this.issuerUri = issuerUri;
  }

  /**
   * Returns OpenID Connect Discovery configuration.
   * <p>
   * This endpoint allows OAuth2 clients to auto-configure by fetching:
   * - Authorization, token, and userinfo endpoints
   * - JWKS URI for signature verification
   * - Supported scopes, claims, and response types
   *
   * @return OpenID Connect Discovery document
   */
  @GetMapping("/.well-known/openid-configuration")
  public Map<String, Object> openidConfiguration() {
    return Map.ofEntries(
        // Required fields
        Map.entry("issuer", issuerUri),
        Map.entry("authorization_endpoint", issuerUri + "/oauth2/authorize"),
        Map.entry("token_endpoint", issuerUri + "/oauth2/token"),
        Map.entry("userinfo_endpoint", issuerUri + "/oauth2/userinfo"),
        Map.entry("jwks_uri", issuerUri + "/.well-known/jwks.json"),

        // Optional endpoints
        Map.entry("revocation_endpoint", issuerUri + "/oauth2/revoke"),
        Map.entry("introspection_endpoint", issuerUri + "/oauth2/introspect"),
        Map.entry("end_session_endpoint", issuerUri + "/oauth2/logout"),

        // Response types supported
        Map.entry("response_types_supported", List.of("code", "token", "id_token")),

        // Response modes supported
        Map.entry("response_modes_supported", List.of("query", "fragment", "form_post")),

        // Grant types supported
        Map.entry("grant_types_supported", List.of(
            "authorization_code",
            "refresh_token",
            "password",
            "client_credentials"
        )),

        // Subject types
        Map.entry("subject_types_supported", List.of("public")),

        // ID token signing algorithms
        Map.entry("id_token_signing_alg_values_supported", List.of("RS256")),

        // Token endpoint authentication methods
        Map.entry("token_endpoint_auth_methods_supported", List.of(
            "client_secret_basic",
            "client_secret_post"
        )),

        // Claims supported
        Map.entry("claims_supported", List.of(
            "sub", "iss", "aud", "exp", "iat",
            "tenant_id", "tenant_code",
            "email", "username", "roles", "propertyIds"
        )),

        // Scopes supported
        Map.entry("scopes_supported", List.of(
            "openid", "profile", "email", "offline_access"
        )),

        // Code challenge methods (PKCE)
        Map.entry("code_challenge_methods_supported", List.of("S256")),

        // Introspection endpoint auth methods
        Map.entry("introspection_endpoint_auth_methods_supported", List.of(
            "client_secret_basic",
            "client_secret_post"
        )),

        // Revocation endpoint auth methods
        Map.entry("revocation_endpoint_auth_methods_supported", List.of(
            "client_secret_basic",
            "client_secret_post"
        ))
    );
  }
}
