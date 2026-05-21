package com.primetech.poc.identity.shared.couchdb.dto;

import java.util.Set;

public record DatabaseAccess(Set<String> names, Set<String> roles) {
}
