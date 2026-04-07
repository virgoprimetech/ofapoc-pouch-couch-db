package com.primetech.poc.identity.shared.exception;

/**
 * Base exception for business logic errors.
 * These are expected errors that should be handled gracefully.
 */
public class BusinessException extends RuntimeException {

  private final String errorCode;

  public BusinessException(String message) {
    super(message);
    this.errorCode = "BUSINESS_ERROR";
  }

  public BusinessException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public BusinessException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
