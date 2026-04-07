package com.primetech.poc.identity.features.user.domain;

import java.util.regex.Pattern;

/**
 * Username value object with validation.
 * <p>
 * Validates that usernames:
 * - Are between 3 and 50 characters
 * - Contain only alphanumeric characters, underscores, and hyphens
 */
public record Username(String value) {

  private static final int MIN_LENGTH = 3;
  private static final int MAX_LENGTH = 50;
  private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

  public Username {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Username cannot be null or blank");
    }
    if (value.length() < MIN_LENGTH) {
      throw new IllegalArgumentException(
          "Username must be at least " + MIN_LENGTH + " characters, but was " + value.length()
      );
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Username must be at most " + MAX_LENGTH + " characters, but was " + value.length()
      );
    }
    if (!USERNAME_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Username can only contain letters, numbers, underscores, and hyphens: " + value
      );
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
