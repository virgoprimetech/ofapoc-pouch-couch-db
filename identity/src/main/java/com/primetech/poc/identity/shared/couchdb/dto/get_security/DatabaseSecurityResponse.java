package com.primetech.poc.identity.shared.couchdb.dto.get_security;

import com.primetech.poc.identity.shared.couchdb.dto.DatabaseAccess;

public record DatabaseSecurityResponse(DatabaseAccess admins, DatabaseAccess members) {
}
