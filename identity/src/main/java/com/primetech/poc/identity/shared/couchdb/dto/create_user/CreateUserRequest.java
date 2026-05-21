package com.primetech.poc.identity.shared.couchdb.dto.create_user;

import java.util.Set;

public record CreateUserRequest(
    String name,
    String password,
    Set<String> roles,
    String type
) {
}
