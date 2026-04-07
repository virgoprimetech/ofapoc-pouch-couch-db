package com.primetech.poc.identity.shared.multitenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Thread-local storage for tenant context.
 * <p>
 * Stores the current tenant's code, schema name, and ID for the duration of a request.
 * Must be cleared in finally blocks to prevent context leakage between requests.
 */
public final class TenantContext {

  private static final Logger log = LoggerFactory.getLogger(TenantContext.class);

  private static final ThreadLocal<String> CURRENT_TENANT_CODE = new ThreadLocal<>();
  private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();
  private static final ThreadLocal<UUID> CURRENT_TENANT_ID = new ThreadLocal<>();

  private TenantContext() {
    // Utility class
  }

  /**
   * Set the current tenant context
   *
   * @param tenantCode the tenant code (used in queries and JWT)
   * @param tenantId   the tenant UUID
   * @param schemaName the PostgreSQL schema name
   */
  public static void setTenant(String tenantCode, UUID tenantId, String schemaName) {
    log.debug("Setting tenant context: code={}, id={}, schema={}", tenantCode, tenantId, schemaName);
    CURRENT_TENANT_CODE.set(tenantCode);
    CURRENT_TENANT_ID.set(tenantId);
    CURRENT_SCHEMA.set(schemaName);
  }

  /**
   * Get the current tenant code
   *
   * @return the tenant code or null if no tenant context is set
   */
  public static String getTenantCode() {
    return CURRENT_TENANT_CODE.get();
  }

  /**
   * Set tenant context using code only (derives schema name)
   *
   * @param tenantCode the tenant code
   */
  public static void setTenantCode(String tenantCode) {
    log.debug("Setting tenant code: {}", tenantCode);
    CURRENT_TENANT_CODE.set(tenantCode);
    CURRENT_SCHEMA.set(codeToSchemaName(tenantCode));
  }

  /**
   * Get the current schema name
   *
   * @return the schema name or null if no tenant context is set
   */
  public static String getSchemaName() {
    return CURRENT_SCHEMA.get();
  }

  /**
   * Get the current tenant ID
   *
   * @return the tenant UUID or null if no tenant context is set
   */
  public static UUID getTenantId() {
    return CURRENT_TENANT_ID.get();
  }

  /**
   * Check if a tenant context is set
   *
   * @return true if tenant context is set
   */
  public static boolean isSet() {
    return CURRENT_TENANT_CODE.get() != null;
  }

  /**
   * Clear the tenant context.
   * MUST be called in finally blocks to prevent context leakage.
   */
  public static void clear() {
    log.debug("Clearing tenant context");
    CURRENT_TENANT_CODE.remove();
    CURRENT_SCHEMA.remove();
    CURRENT_TENANT_ID.remove();
  }

  /**
   * Convert a tenant code to a schema name.
   * E.g., "acme" -> "tenant_acme", "my-company" -> "tenant_my_company"
   *
   * @param code the tenant code
   * @return the schema name
   */
  public static String codeToSchemaName(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Tenant code cannot be null or blank");
    }
    // Normalize: lowercase, replace non-alphanumeric with underscore
    String normalized = code.toLowerCase().replaceAll("[^a-z0-9]", "_");
    return "tenant_" + normalized;
  }

  /**
   * Extract tenant code from schema name.
   * E.g., "tenant_acme" -> "acme"
   *
   * @param schemaName the schema name
   * @return the tenant code
   */
  public static String schemaNameToCode(String schemaName) {
    if (schemaName == null || !schemaName.startsWith("tenant_")) {
      throw new IllegalArgumentException("Invalid tenant schema name: " + schemaName);
    }
    return schemaName.substring(7); // Remove "tenant_" prefix
  }
}
