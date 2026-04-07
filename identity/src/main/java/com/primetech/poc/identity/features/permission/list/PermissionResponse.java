package com.primetech.poc.identity.features.permission.list;

import java.time.Instant;
import java.util.UUID;

/**
 * Permission Response
 */
public record PermissionResponse(
    UUID id,
    String code,
    String resource,
    String action,
    String displayName,
    String description,
    Instant createdAt
) {
}
