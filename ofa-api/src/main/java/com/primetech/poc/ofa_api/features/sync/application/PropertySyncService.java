package com.primetech.poc.ofa_api.features.sync.application;

import com.primetech.poc.ofa_api.features.property.domain.Property;
import com.primetech.poc.ofa_api.features.property.domain.PropertyStatus;
import com.primetech.poc.ofa_api.features.property.domain.PropertyType;
import com.primetech.poc.ofa_api.features.property.infrastructure.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class PropertySyncService {

  private static final Logger log = LoggerFactory.getLogger(PropertySyncService.class);

  private final PropertyRepository propertyRepository;

  public PropertySyncService(PropertyRepository propertyRepository) {
    this.propertyRepository = propertyRepository;
  }

  private static String nullIfEmpty(String value) {
    return (value == null || value.isEmpty()) ? null : value;
  }

  private static Instant parseInstant(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(value));
    } catch (Exception e) {
      return null;
    }
  }

  private static BigDecimal parseBigDecimal(JsonNode doc, String field) {
    JsonNode node = doc.path(field);
    if (node.isNull() || node.isMissingNode() || !node.isNumber()) {
      return null;
    }
    return node.decimalValue();
  }

  private static Integer parseInteger(JsonNode doc, String field) {
    JsonNode node = doc.path(field);
    if (node.isNull() || node.isMissingNode() || !node.isNumber()) {
      return null;
    }
    return node.asInt();
  }

  private static <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass, T defaultVal) {
    if (value == null || value.isEmpty()) {
      return defaultVal;
    }
    try {
      return Enum.valueOf(enumClass, value);
    } catch (IllegalArgumentException e) {
      return defaultVal;
    }
  }

  @Transactional
  public void syncDocument(JsonNode doc) {
    String couchId = doc.path("_id").asText();
    UUID id = extractUuid(couchId);
    String rev = doc.path("_rev").asText();

    boolean deleted = !doc.path("deleted_at").isNull()
        && !doc.path("deleted_at").asText().isEmpty();

    if (deleted) {
      softDelete(id, rev, doc);
      return;
    }

    Property property = propertyRepository.findById(id).orElse(null);

    if (property == null) {
      property = new Property();
      property.setId(id);
      applyFields(property, doc);
      property.setCouchdbRev(rev);
      propertyRepository.save(property);
      log.info("[sync] Created property {} from CouchDB", id);
      return;
    }

    // Skip if CouchDB revision already processed
    if (rev.equals(property.getCouchdbRev())) {
      return;
    }

    // Only update if CouchDB updated_at >= PG updated_at
    Instant couchUpdatedAt = parseInstant(doc.path("updated_at").asText());
    if (couchUpdatedAt != null && property.getUpdatedAt() != null
        && couchUpdatedAt.isBefore(property.getUpdatedAt())) {
      log.debug("[sync] Skipping property {} — PG is newer", id);
      return;
    }

    applyFields(property, doc);
    property.setCouchdbRev(rev);
    propertyRepository.save(property);
    log.info("[sync] Updated property {} from CouchDB", id);
  }

  private void softDelete(UUID id, String rev, JsonNode doc) {
    Property property = propertyRepository.findById(id).orElse(null);
    if (property == null) {
      return;
    }

    if (rev.equals(property.getCouchdbRev())) {
      return;
    }

    Instant deletedAt = parseInstant(doc.path("deleted_at").asText());
    property.setDeletedAt(deletedAt != null ? deletedAt : Instant.now());
    property.setStatus(PropertyStatus.ARCHIVED);
    property.setCouchdbRev(rev);
    propertyRepository.save(property);
    log.info("[sync] Soft-deleted property {}", id);
  }

  private void applyFields(Property property, JsonNode doc) {
    property.setName(nullIfEmpty(doc.path("name").asText()));
    property.setPropertyType(parseEnum(doc.path("property_type").asText(), PropertyType.class, PropertyType.OTHER));
    property.setStatus(parseEnum(doc.path("status").asText(), PropertyStatus.class, PropertyStatus.DRAFT));
    property.setDescription(nullIfEmpty(doc.path("description").asText()));
    property.setAddressLine1(nullIfEmpty(doc.path("address_line1").asText()));
    property.setAddressLine2(nullIfEmpty(doc.path("address_line2").asText()));
    property.setCity(nullIfEmpty(doc.path("city").asText()));
    property.setStateProvince(nullIfEmpty(doc.path("state_province").asText()));
    property.setPostalCode(nullIfEmpty(doc.path("postal_code").asText()));
    property.setCountry(nullIfEmpty(doc.path("country").asText()));
    property.setLatitude(parseBigDecimal(doc, "latitude"));
    property.setLongitude(parseBigDecimal(doc, "longitude"));
    property.setPhone(nullIfEmpty(doc.path("phone").asText()));
    property.setEmail(nullIfEmpty(doc.path("email").asText()));
    property.setWebsite(nullIfEmpty(doc.path("website").asText()));
    property.setTimezone(nullIfEmpty(doc.path("timezone").asText()));
    property.setCurrency(nullIfEmpty(doc.path("currency").asText()));
    property.setTotalRooms(parseInteger(doc, "total_rooms"));
    property.setTenantId(nullIfEmpty(doc.path("tenant_id").asText()));

    Instant createdAt = parseInstant(doc.path("created_at").asText());
    if (createdAt != null) {
      property.setCreatedAt(createdAt);
    }

    Instant updatedAt = parseInstant(doc.path("updated_at").asText());
    if (updatedAt != null) {
      property.setUpdatedAt(updatedAt);
    }
  }

  private UUID extractUuid(String couchId) {
    if (couchId.startsWith("property::")) {
      return UUID.fromString(couchId.substring("property::".length()));
    }
    return UUID.fromString(couchId);
  }
}
