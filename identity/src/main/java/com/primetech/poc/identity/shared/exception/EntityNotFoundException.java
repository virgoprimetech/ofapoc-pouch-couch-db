package com.primetech.poc.identity.shared.exception;

/**
 * Exception thrown when an entity is not found.
 */
public class EntityNotFoundException extends BusinessException {

  public EntityNotFoundException(String entityName, Object identifier) {
    super("ENTITY_NOT_FOUND", String.format("%s not found with identifier: %s", entityName, identifier));
  }

  public EntityNotFoundException(String message) {
    super("ENTITY_NOT_FOUND", message);
  }
}
