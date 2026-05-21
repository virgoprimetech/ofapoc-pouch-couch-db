package com.primetech.poc.identity.shared.couchdb.dto.update_security;

import com.primetech.poc.identity.shared.couchdb.dto.DatabaseAccess;

public record UpdateSecurityRequest(
    DatabaseAccess admins,
    DatabaseAccess members
) {
}
