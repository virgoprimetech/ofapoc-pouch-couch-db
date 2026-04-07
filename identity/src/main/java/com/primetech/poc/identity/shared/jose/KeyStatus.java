package com.primetech.poc.identity.shared.jose;

/**
 * Status of a signing key in the key rotation lifecycle.
 */
public enum KeyStatus {

  /**
   * Key is active and used for signing new tokens.
   */
  ACTIVE,

  /**
   * Key is deprecated but still valid for token verification.
   * Used during key rotation to allow graceful transition.
   */
  DEPRECATED,

  /**
   * Key is revoked and no longer valid for any operation.
   * Private key should be securely deleted.
   */
  REVOKED
}
