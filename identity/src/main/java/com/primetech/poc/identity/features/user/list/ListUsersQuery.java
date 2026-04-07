package com.primetech.poc.identity.features.user.list;

import com.primetech.poc.identity.features.user.domain.UserStatus;

/**
 * List Users Query
 */
public record ListUsersQuery(UserStatus status) {
}
