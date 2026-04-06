package com.primetech.poc.ofa_api.features.sync.application;

//import tools.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;

import com.primetech.poc.ofa_api.features.property.domain.Property;
import com.primetech.poc.ofa_api.features.property.domain.PropertyChangedEvent;
import com.primetech.poc.ofa_api.features.property.infrastructure.PropertyRepository;
import com.primetech.poc.ofa_api.features.sync.config.SyncProperties;
import com.primetech.poc.ofa_api.features.sync.infrastructure.CouchDbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ReverseSyncService {

  private static final Logger log = LoggerFactory.getLogger(ReverseSyncService.class);

  private final CouchDbClient couchDbClient;
  private final PropertyRepository propertyRepository;
  private final SyncProperties props;
  private final ObjectMapper objectMapper;

  public ReverseSyncService(CouchDbClient couchDbClient,
                            PropertyRepository propertyRepository,
                            SyncProperties props,
                            ObjectMapper objectMapper) {
    this.couchDbClient = couchDbClient;
    this.propertyRepository = propertyRepository;
    this.props = props;
    this.objectMapper = objectMapper;
  }

  private static String nullSafe(String value) {
    return value != null ? value : "";
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPropertyChanged(PropertyChangedEvent event) {
    if (!props.enabled()) {
      return;
    }

    try {
      Property property = propertyRepository.findById(event.propertyId()).orElse(null);
      if (property == null) {
        log.warn("[reverse-sync] Property {} not found — skipping", event.propertyId());
        return;
      }

      String docId = "property::" + property.getId();

      // Fetch current _rev from CouchDB (or use stored rev)
      String rev = property.getCouchdbRev();
      if (rev == null || rev.isEmpty()) {
        JsonNode existing = couchDbClient.getDocument(docId);
        if (existing != null && existing.has("_rev")) {
          rev = existing.get("_rev").asString();
        }
      }

      ObjectNode doc = objectMapper.createObjectNode();
      doc.put("_id", docId);
      if (rev != null) {
        doc.put("_rev", rev);
      }
      doc.put("type", "property");
      doc.put("name", property.getName());
      doc.put("property_type", property.getPropertyType().name());
      doc.put("status", property.getStatus().name());
      doc.put("description", nullSafe(property.getDescription()));
      doc.put("address_line1", nullSafe(property.getAddressLine1()));
      doc.put("address_line2", nullSafe(property.getAddressLine2()));
      doc.put("city", nullSafe(property.getCity()));
      doc.put("state_province", nullSafe(property.getStateProvince()));
      doc.put("postal_code", nullSafe(property.getPostalCode()));
      doc.put("country", nullSafe(property.getCountry()));

      if (property.getLatitude() != null) {
        doc.put("latitude", property.getLatitude());
      } else {
        doc.putNull("latitude");
      }
      if (property.getLongitude() != null) {
        doc.put("longitude", property.getLongitude());
      } else {
        doc.putNull("longitude");
      }

      doc.put("phone", nullSafe(property.getPhone()));
      doc.put("email", nullSafe(property.getEmail()));
      doc.put("website", nullSafe(property.getWebsite()));
      doc.put("timezone", nullSafe(property.getTimezone()));
      doc.put("currency", nullSafe(property.getCurrency()));

      if (property.getTotalRooms() != null) {
        doc.put("total_rooms", property.getTotalRooms());
      } else {
        doc.putNull("total_rooms");
      }

      doc.put("tenant_id", property.getTenantId());

      if (property.getCreatedAt() != null) {
        doc.put("created_at", property.getCreatedAt().toString());
      }
      if (property.getUpdatedAt() != null) {
        doc.put("updated_at", property.getUpdatedAt().toString());
      }
      if (property.getDeletedAt() != null) {
        doc.put("deleted_at", property.getDeletedAt().toString());
      } else {
        doc.putNull("deleted_at");
      }

      String jsonBody = objectMapper.writeValueAsString(doc);
      couchDbClient.upsertDocument(docId, rev, jsonBody);

      log.info("[reverse-sync] Pushed property {} to CouchDB ({})", property.getId(), event.changeType());

    } catch (Exception e) {
      log.error("[reverse-sync] Failed to push property {} to CouchDB: {}",
          event.propertyId(), e.getMessage(), e);
    }
  }
}
