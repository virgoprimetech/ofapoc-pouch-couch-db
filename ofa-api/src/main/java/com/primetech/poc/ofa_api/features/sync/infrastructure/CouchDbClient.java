package com.primetech.poc.ofa_api.features.sync.infrastructure;

//import tools.jackson.databind.JsonNode;

import com.primetech.poc.ofa_api.features.sync.config.SyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CouchDbClient {
  private final RestClient restClient;
  private final String database;

  public CouchDbClient(RestClient couchDbRestClient, SyncProperties props) {
    this.restClient = couchDbRestClient;
    this.database = props.couchdb().database();
  }

  public String getUpdateSeq() {
    JsonNode response = restClient.get()
        .uri("/{db}", database)
        .headers(headers -> headers.setBasicAuth("admin", "admin")) // Add basic auth if needed
        .retrieve()
        .body(JsonNode.class);

    if (response != null && response.has("update_seq")) {
      return response.get("update_seq").asText();
    }
    return "0";
  }

  public ChangesResponse getChanges(String sinceSeq) {
    JsonNode response = restClient.post()
        .uri(uriBuilder -> uriBuilder
            .path("/{db}/_changes")
            .queryParam("since", sinceSeq)
            .queryParam("include_docs", "true")
            .queryParam("filter", "_selector")
            .build(database))
        .body(Map.of("selector", Map.of("type", "property")))
        .headers(headers -> headers.setBasicAuth("admin", "admin")) // Add basic auth if needed
        .retrieve()
        .body(JsonNode.class);

    if (response == null) {
      return new ChangesResponse(sinceSeq, List.of());
    }

    String lastSeq = response.path("last_seq").asText(sinceSeq);
    List<JsonNode> docs = List.of();
    if (response.has("results") && response.get("results").isArray()) {
      docs = List.copyOf(response.get("results").findValues("doc").stream()
          .filter(doc -> doc.has("type") && "property".equals(doc.get("type").asText()))
          .toList());
    }

    return new ChangesResponse(lastSeq, docs);
  }

  public List<JsonNode> getAllDocs() {
    final var client = restClient.get()
        .uri(uriBuilder -> uriBuilder
                .path("/{db}/_all_docs")
                .queryParam("include_docs", "true")
                .build(database));
    JsonNode response = client
        .headers(headers -> headers.setBasicAuth("admin", "admin")) // Add basic auth if needed
        .retrieve()
        .body(JsonNode.class);

    if (response == null || !response.has("rows")) {
      return List.of();
    }

    return response.get("rows").findValues("doc").stream()
        .filter(doc -> doc.has("type") && "property".equals(doc.get("type").asText()))
        .toList();
  }

  public tools.jackson.databind.JsonNode getDocument(String docId) {
    return restClient.get()
        .uri("/{db}/{docId}", database, docId)
        .retrieve()
        .body(JsonNode.class);
  }

  public void upsertDocument(String docId, String rev, String jsonBody) {
    restClient.put()
        .uri(uriBuilder -> uriBuilder
            .path("/{db}/{docId}")
            .queryParam("rev", rev)
            .build(database, docId))
        .header("Content-Type", "application/json")
        .body(jsonBody)
        .retrieve()
        .toBodilessEntity();

    log.info("[couchdb] Upserted document {}", docId);
  }

  /**
   * Fetch all property documents that have conflicts.
   * Uses _all_docs with conflicts=true, then filters for docs with _conflicts array.
   */
  public List<ConflictInfo> getConflicts() {
    JsonNode response = restClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/{db}/_all_docs")
            .queryParam("include_docs", "true")
            .queryParam("conflicts", "true")
            .queryParam("startkey", "\"property::\"")
            .queryParam("endkey", "\"property::\\uffff\"")
            .build(database))
        .retrieve()
        .body(JsonNode.class);

    if (response == null || !response.has("rows")) {
      return List.of();
    }

    return response.get("rows").valueStream()
        .map(row -> row.path("doc"))
        .filter(doc -> doc.has("type") && "property".equals(doc.get("type").asText()))
        .filter(doc -> doc.has("_conflicts") && doc.get("_conflicts").isArray() && !doc.get("_conflicts").isEmpty())
        .map(doc -> new ConflictInfo(
            doc.path("_id").asString(),
            doc.path("_rev").asString(),
            List.copyOf(doc.get("_conflicts").valueStream()
                .map(JsonNode::asString).toList())
        ))
        .toList();
  }

  public record ChangesResponse(String lastSeq, List<JsonNode> docs) {
  }

  public record ConflictInfo(String docId, String winningRev, List<String> conflictingRevs) {
  }
}
