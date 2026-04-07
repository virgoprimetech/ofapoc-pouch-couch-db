package com.primetech.poc.identity.shared.exception;

/**
 * Exception thrown when attempting to create an entity that already exists.
 */
public class DuplicateEntityException extends BusinessException {

  public DuplicateEntityException(String entityName, String field, Object value) {
    super("DUPLICATE_ENTITY", String.format("%s already exists with %s: %s", entityName, field, value));
  }

  public DuplicateEntityException(String message) {
    super("DUPLICATE_ENTITY", message);
  }
}
