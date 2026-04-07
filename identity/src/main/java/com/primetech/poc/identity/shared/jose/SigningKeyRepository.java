package com.primetech.poc.identity.shared.jose;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing persistent signing keys.
 */
@Repository
public interface SigningKeyRepository extends JpaRepository<SigningKeyEntity, UUID> {

  /**
   * Find the current active key for signing new tokens.
   * Returns the most recently created active key.
   */
  @Query("SELECT k FROM SigningKeyEntity k WHERE k.status = 'ACTIVE' ORDER BY k.createdAt DESC LIMIT 1")
  Optional<SigningKeyEntity> findCurrentActiveKey();

  /**
   * Find all active keys ordered by creation date (newest first).
   */
  @Query("SELECT k FROM SigningKeyEntity k WHERE k.status = 'ACTIVE' ORDER BY k.createdAt DESC")
  List<SigningKeyEntity> findActiveKeys();

  /**
   * Find all valid keys for JWKS endpoint.
   * Includes both ACTIVE and DEPRECATED keys for verification during rotation.
   */
  @Query("SELECT k FROM SigningKeyEntity k WHERE k.status IN ('ACTIVE', 'DEPRECATED') ORDER BY k.createdAt DESC")
  List<SigningKeyEntity> findValidKeys();

  /**
   * Find a key by its Key ID (kid).
   */
  Optional<SigningKeyEntity> findByKeyId(String keyId);

  /**
   * Find deprecated keys that were rotated before a given timestamp.
   * Used for cleanup of old keys after rotation grace period.
   */
  @Query("SELECT k FROM SigningKeyEntity k WHERE k.status = 'DEPRECATED' AND k.rotatedAt < :cutoff")
  List<SigningKeyEntity> findDeprecatedKeysRotatedBefore(java.time.Instant cutoff);
}
