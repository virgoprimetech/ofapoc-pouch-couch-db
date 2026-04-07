package com.primetech.poc.identity.features.user.domain;

/**
 * User status enumeration.
 */
public enum UserStatus {
  PENDING,      // User created but not yet verified
  ACTIVE,       // User is active and can authenticate
  SUSPENDED,    // User is temporarily suspended
  DEACTIVATED   // User is permanently deactivated
}
