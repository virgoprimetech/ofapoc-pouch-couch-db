package com.primetech.poc.identity.features.tenant.create;

import com.primetech.poc.identity.features.tenant.domain.TenantStatus;

import java.time.Instant;

/**
 * Tenant Response
 */
public record TenantResponse(
    String id,
    String code,
    String schemaName,
    String displayName,
    TenantStatus status,
    Instant createdAt
) {
}
