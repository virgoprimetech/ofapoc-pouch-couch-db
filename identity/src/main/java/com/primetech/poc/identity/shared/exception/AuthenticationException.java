package com.primetech.poc.identity.shared.exception;

/**
 * Exception thrown for authentication failures.
 */
public class AuthenticationException extends BusinessException {

  public AuthenticationException(String message) {
    super("AUTHENTICATION_FAILED", message);
  }

  public AuthenticationException(String message, Throwable cause) {
    super("AUTHENTICATION_FAILED", message, cause);
  }
}
