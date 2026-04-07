package com.primetech.poc.identity.shared.jose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for persistent RSA key storage with rotation support.
 * <p>
 * Keys are stored in the public schema since they're shared across all tenants.
 * Private keys are encrypted at rest using AES-GCM.
 */
@Entity
@Table(name = "signing_keys", schema = "public", indexes = {
    @Index(name = "idx_signing_keys_status", columnList = "status"),
    @Index(name = "idx_signing_keys_key_id", columnList = "key_id", unique = true)
})
public class SigningKeyEntity {

  @Id
  private UUID id;

  @Column(name = "key_id", nullable = false, unique = true)
  private String keyId;

  @Column(name = "key_type", nullable = false)
  private String keyType = "RSA";

  @Column(name = "algorithm", nullable = false)
  private String algorithm = "RS256";

  @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
  private String publicKey;

  @Column(name = "private_key", nullable = false, columnDefinition = "TEXT")
  private String privateKey;

  @Column(name = "key_size", nullable = false)
  private Integer keySize = 2048;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private KeyStatus status = KeyStatus.ACTIVE;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "rotated_at")
  private Instant rotatedAt;

  // Default constructor for JPA
  public SigningKeyEntity() {
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(String keyId) {
    this.keyId = keyId;
  }

  public String getKeyType() {
    return keyType;
  }

  public void setKeyType(String keyType) {
    this.keyType = keyType;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(String privateKey) {
    this.privateKey = privateKey;
  }

  public Integer getKeySize() {
    return keySize;
  }

  public void setKeySize(Integer keySize) {
    this.keySize = keySize;
  }

  public KeyStatus getStatus() {
    return status;
  }

  public void setStatus(KeyStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getRotatedAt() {
    return rotatedAt;
  }

  public void setRotatedAt(Instant rotatedAt) {
    this.rotatedAt = rotatedAt;
  }
}
