package com.primetech.poc.identity.shared.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hibernate Tenant Identifier Resolver.
 * <p>
 * Resolves the current tenant identifier from the TenantContext thread-local storage.
 * This is used by Hibernate to determine which schema to use for database operations.
 * <p>
 * Compatible with Hibernate 7.x / Spring Boot 4.x
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

  private static final Logger log = LoggerFactory.getLogger(TenantIdentifierResolver.class);
  private static final String DEFAULT_TENANT = "public";

  @Override
  public String resolveCurrentTenantIdentifier() {
    String schemaName = TenantContext.getSchemaName();

    if (schemaName != null && !schemaName.isBlank()) {
      log.trace("Resolved tenant schema: {}", schemaName);
      return schemaName;
    }

    log.trace("No tenant context set, using default schema: {}", DEFAULT_TENANT);
    return DEFAULT_TENANT;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    // Return false to allow new sessions with different tenant identifiers
    // This is important for schema-based multi-tenancy where different
    // requests may have different tenant contexts
    return false;
  }
}
