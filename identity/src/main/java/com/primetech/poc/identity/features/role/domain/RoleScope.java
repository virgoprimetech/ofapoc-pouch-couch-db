package com.primetech.poc.identity.features.role.domain;

/**
 * Role scope enumeration.
 */
public enum RoleScope {
  GLOBAL,     // System-wide role (e.g., SYSTEM_ADMIN)
  CHAIN,      // Chain-level role (e.g., TENANT_ADMIN)
  PROPERTY    // Property-level role (e.g., PROPERTY_MANAGER)
}
