package com.primetech.poc.identity.shared.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent RSA Key Configuration for OAuth2 JWT signing.
 * <p>
 * Loads RSA key pair from database or generates and persists a new one on first startup.
 * Keys are encrypted at rest using AES-GCM via {@link KeyEncryptionService}.
 * <p>
 * Supports:
 * <ul>
 *   <li>Key persistence across application restarts</li>
 *   <li>Key rotation with grace period for verification</li>
 *   <li>Multiple active keys for verification during rotation</li>
 * </ul>
 */
@Configuration
@Slf4j
public class PersistentRsaKeyConfig {

  private final SigningKeyRepository signingKeyRepository;
  private final KeyEncryptionService keyEncryptionService;

  public PersistentRsaKeyConfig(
      SigningKeyRepository signingKeyRepository,
      KeyEncryptionService keyEncryptionService) {
    this.signingKeyRepository = signingKeyRepository;
    this.keyEncryptionService = keyEncryptionService;
  }

  /**
   * RSA key pair for JWT signing.
   * <p>
   * Loads existing active key from database or generates and persists a new one.
   */
  @Bean
  @Primary
  public RSAKey rsaKey() {
    // Try to load existing active key
    Optional<SigningKeyEntity> existingKey = signingKeyRepository.findCurrentActiveKey();

    if (existingKey.isPresent()) {
      log.info("Loading existing RSA key with KID: {}", existingKey.get().getKeyId());
      return loadKeyFromEntity(existingKey.get());
    }

    // Generate and persist new key
    log.warn("No active RSA key found in database, generating new key pair");
    RSAKey newKey = generateNewKey();
    persistKey(newKey);
    return newKey;
  }

  /**
   * JWK Source for JWT encoding (signing).
   * <p>
   * Returns the current signing key with private key for JWT signing.
   * This is used by the JwtEncoder to sign tokens.
   */
  @Bean
  public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
    // For encoding, we need the full key (including private key)
    JWKSet jwkSet = new JWKSet(rsaKey);
    return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
  }

  /**
   * Get all valid public keys for JWKS endpoint.
   * <p>
   * Returns ACTIVE and DEPRECATED public keys for token verification.
   * This is used by the JwksEndpoint to expose public keys.
   */
  public List<JWK> getValidPublicKeys() {
    List<SigningKeyEntity> validKeys = signingKeyRepository.findValidKeys();

    if (validKeys.isEmpty()) {
      // Fallback to current signing key if no keys in database yet
      RSAKey currentKey = rsaKey();
      return List.of(currentKey.toPublicJWK());
    }

    return validKeys.stream()
        .map(this::entityToPublicJwk)
        .map(JWK.class::cast)
        .toList();
  }

  /**
   * JWT Encoder using RSA private key for signing.
   */
  @Bean
  public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
    return new NimbusJwtEncoder(jwkSource);
  }

  /**
   * JWT Decoder using RSA public key for verification.
   */
  @Bean
  public JwtDecoder jwtDecoder(RSAKey rsaKey) throws JOSEException {
    return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
  }

  /**
   * Generate a new RSA-2048 key pair.
   */
  private RSAKey generateNewKey() {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      KeyPair keyPair = keyPairGenerator.generateKeyPair();

      return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
          .privateKey((RSAPrivateKey) keyPair.getPrivate())
          .keyID(UUID.randomUUID().toString())
          .build();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate RSA key pair", e);
    }
  }

  /**
   * Persist a new RSA key to the database.
   */
  private void persistKey(RSAKey key) {
    try {
      SigningKeyEntity entity = new SigningKeyEntity();
      entity.setId(UUID.randomUUID());
      entity.setKeyId(key.getKeyID());
      entity.setKeyType("RSA");
      entity.setAlgorithm("RS256");
      entity.setPublicKey(key.toPublicJWK().toJSONString());
      entity.setPrivateKey(keyEncryptionService.encrypt(key.toJSONString()));
      entity.setKeySize(2048);
      entity.setStatus(KeyStatus.ACTIVE);
      entity.setCreatedAt(Instant.now());

      signingKeyRepository.save(entity);
      log.info("Persisted new RSA key with KID: {}", key.getKeyID());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to persist RSA key", e);
    }
  }

  /**
   * Load RSA key from database entity.
   */
  private RSAKey loadKeyFromEntity(SigningKeyEntity entity) {
    try {
      String privateKeyJson = keyEncryptionService.decrypt(entity.getPrivateKey());
      return RSAKey.parse(privateKeyJson);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load RSA key from database", e);
    }
  }

  /**
   * Convert entity to public JWK for JWKS endpoint.
   */
  private RSAKey entityToPublicJwk(SigningKeyEntity entity) {
    try {
      RSAKey key = RSAKey.parse(entity.getPublicKey());
      return key.toPublicJWK();
    } catch (Exception e) {
      log.error("Failed to parse key with KID: {}", entity.getKeyId(), e);
      throw new IllegalStateException("Failed to parse key", e);
    }
  }
}
