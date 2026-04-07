package com.primetech.poc.identity.features.user.domain;

import java.util.regex.Pattern;

/**
 * Email value object with validation.
 */
public record Email(String value) {

  private static final Pattern EMAIL_PATTERN = Pattern.compile(
      "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
  );

  public Email {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Email cannot be null or blank");
    }
    if (!EMAIL_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid email format: " + value);
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
