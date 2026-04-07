package com.primetech.poc.identity.features.tenant.domain;

/**
 * Tenant status enumeration.
 */
public enum TenantStatus {
  /**
   * Tenant is active and can be used.
   */
  ACTIVE,

  /**
   * Tenant is suspended (e.g., due to billing issues).
   * Users cannot log in, but data is preserved.
   */
  SUSPENDED,

  /**
   * Tenant is permanently deactivated.
   * Data may be scheduled for deletion.
   */
  DEACTIVATED
}
