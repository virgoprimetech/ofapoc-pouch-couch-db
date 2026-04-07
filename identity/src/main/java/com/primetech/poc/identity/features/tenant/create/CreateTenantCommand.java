package com.primetech.poc.identity.features.tenant.create;

/**
 * Create Tenant Command
 */
public record CreateTenantCommand(
    String code,
    String displayName,
    String adminUsername,
    String adminEmail,
    String adminPassword
) {
}
