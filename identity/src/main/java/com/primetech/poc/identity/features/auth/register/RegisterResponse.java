package com.primetech.poc.identity.features.auth.register;

import com.primetech.poc.identity.features.user.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Register Response
 */
public record RegisterResponse(
    UUID id,
    String username,
    String email,
    UserStatus status,
    String firstName,
    String lastName,
    Instant createdAt
) {
}
