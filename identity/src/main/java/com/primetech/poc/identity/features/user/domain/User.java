package com.primetech.poc.identity.features.user.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * User Domain Model
 * <p>
 * Pure domain model without JPA annotations.
 * Represents a user account in the identity system.
 */
public record User(
    UUID id,
    Username username,
    Email email,
    String passwordHash,
    UserStatus status,
    String firstName,
    String lastName,
    String phone,
    String preferredLanguage,
    String timezone,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {
  /**
   * Check if the user is soft-deleted
   */
  public boolean isDeleted() {
    return deletedAt != null;
  }

  /**
   * Check if the user is active
   */
  public boolean isActive() {
    return status == UserStatus.ACTIVE && !isDeleted();
  }

  /**
   * Get the full name of the user
   */
  public String fullName() {
    if (firstName == null && lastName == null) {
      return null;
    }
    if (firstName == null) {
      return lastName;
    }
    if (lastName == null) {
      return firstName;
    }
    return firstName + " " + lastName;
  }

  /**
   * Create a new User with updated timestamp
   */
  public User withUpdatedAt(Instant newUpdatedAt) {
    return new User(
        id, username, email, passwordHash, status,
        firstName, lastName, phone,
        preferredLanguage, timezone,
        createdAt, newUpdatedAt, deletedAt
    );
  }

  /**
   * Create a new User with deleted timestamp (soft delete)
   */
  public User withDeletedAt(Instant newDeletedAt) {
    return new User(
        id, username, email, passwordHash, status,
        firstName, lastName, phone,
        preferredLanguage, timezone,
        createdAt, updatedAt, newDeletedAt
    );
  }

  /**
   * Create a new User with updated status
   */
  public User withStatus(UserStatus newStatus) {
    return new User(
        id, username, email, passwordHash, newStatus,
        firstName, lastName, phone,
        preferredLanguage, timezone,
        createdAt, updatedAt, deletedAt
    );
  }
}
