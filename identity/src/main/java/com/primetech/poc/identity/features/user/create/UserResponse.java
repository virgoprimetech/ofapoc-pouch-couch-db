package com.primetech.poc.identity.features.user.create;

import com.primetech.poc.identity.features.user.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * User Response
 */
public record UserResponse(
    UUID id,
    String username,
    String email,
    UserStatus status,
    String firstName,
    String lastName,
    String phone,
    String preferredLanguage,
    String timezone,
    Instant createdAt,
    Instant updatedAt
) {
}
