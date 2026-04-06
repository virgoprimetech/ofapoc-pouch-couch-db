package com.primetech.poc.ofa_api.features.sync.api;

import com.primetech.poc.ofa_api.features.sync.infrastructure.CouchDbClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

  private final CouchDbClient couchDbClient;

  public SyncController(CouchDbClient couchDbClient) {
    this.couchDbClient = couchDbClient;
  }

  @GetMapping("/conflicts")
  public List<ConflictResponse> getConflicts() {
    return couchDbClient.getConflicts().stream()
        .map(c -> new ConflictResponse(c.docId(), c.winningRev(), c.conflictingRevs()))
        .toList();
  }
}
