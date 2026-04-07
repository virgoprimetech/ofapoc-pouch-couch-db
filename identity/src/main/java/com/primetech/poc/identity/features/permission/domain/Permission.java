package com.primetech.poc.identity.features.permission.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Permission Domain Model
 * <p>
 * Represents a permission in the system (resource:action format).
 */
public record Permission(
    UUID id,
    String code,
    String resource,
    String action,
    String displayName,
    String description,
    Instant createdAt
) {
}
