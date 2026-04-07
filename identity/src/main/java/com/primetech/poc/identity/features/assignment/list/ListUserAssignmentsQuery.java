package com.primetech.poc.identity.features.assignment.list;

import java.util.UUID;

/**
 * List User Assignments Query
 */
public record ListUserAssignmentsQuery(UUID userId, boolean validOnly) {
}
