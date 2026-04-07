package com.primetech.poc.identity.shared.jose;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * JWKS Endpoint exposing public keys for JWT verification.
 * <p>
 * Clients can fetch public keys from this endpoint to verify JWT signatures.
 * Follows RFC 7517 (JSON Web Key (JWK)).
 * <p>
 * Returns all valid keys (ACTIVE + DEPRECATED) to support key rotation.
 * During rotation, both old and new public keys are available for verification.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class JwksEndpoint {

  private final PersistentRsaKeyConfig persistentRsaKeyConfig;

  /**
   * Returns the JWK Set containing all valid public keys.
   * <p>
   * Includes both ACTIVE and DEPRECATED keys to support verification
   * during key rotation. Clients should cache this response but refresh
   * when they encounter an unknown key ID (kid).
   *
   * @return JWK Set as a Map
   */
  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> jwks() {
    List<JWK> jwks = persistentRsaKeyConfig.getValidPublicKeys();

    if (jwks.isEmpty()) {
      log.warn("No valid signing keys found");
      // Return empty JWK set - this should not happen in normal operation
      return new JWKSet(List.of()).toJSONObject();
    }

    log.debug("Returning {} public keys for JWKS", jwks.size());
    JWKSet jwkSet = new JWKSet(jwks);
    return jwkSet.toJSONObject();
  }
}
