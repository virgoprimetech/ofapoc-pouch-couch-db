package com.primetech.poc.ofa_api.features.property.domain;

import java.time.Instant;
import java.util.UUID;

public record PropertyChangedEvent(
    UUID propertyId,
    String changeType,
    Instant occurredAt
) {
  public static PropertyChangedEvent created(UUID propertyId) {
    return new PropertyChangedEvent(propertyId, "CREATED", Instant.now());
  }

  public static PropertyChangedEvent updated(UUID propertyId) {
    return new PropertyChangedEvent(propertyId, "UPDATED", Instant.now());
  }

  public static PropertyChangedEvent deleted(UUID propertyId) {
    return new PropertyChangedEvent(propertyId, "DELETED", Instant.now());
  }
}
