package com.primetech.poc.identity.features.user.update;

import com.primetech.poc.identity.features.user.domain.UserStatus;

/**
 * Update User Request
 */
public record UpdateUserRequest(
    UserStatus status,
    String firstName,
    String lastName,
    String phone,
    String preferredLanguage,
    String timezone
) {
}
