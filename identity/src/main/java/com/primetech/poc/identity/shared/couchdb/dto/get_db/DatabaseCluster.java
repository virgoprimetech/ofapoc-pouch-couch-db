package com.primetech.poc.identity.shared.couchdb.dto.get_db;

public record DatabaseCluster(
    Integer q,
    Integer n,
    Integer w,
    Integer r
) {
}
