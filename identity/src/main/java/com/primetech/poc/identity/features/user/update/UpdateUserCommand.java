package com.primetech.poc.identity.features.user.update;

import com.primetech.poc.identity.features.user.domain.UserStatus;

import java.util.UUID;

/**
 * Update User Command
 * <p>
 * Input data for updating an existing user.
 */
public record UpdateUserCommand(
    UUID id,
    UserStatus status,
    String firstName,
    String lastName,
    String phone,
    String preferredLanguage,
    String timezone
) {
}
