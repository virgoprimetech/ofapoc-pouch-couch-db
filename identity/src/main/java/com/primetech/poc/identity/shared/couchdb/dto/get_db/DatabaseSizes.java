package com.primetech.poc.identity.shared.couchdb.dto.get_db;

public record DatabaseSizes(
    Long file,
    Long external,
    Long active
) {
}
