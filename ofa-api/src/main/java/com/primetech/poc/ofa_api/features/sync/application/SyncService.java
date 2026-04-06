package com.primetech.poc.ofa_api.features.sync.application;

import com.primetech.poc.ofa_api.features.sync.config.SyncProperties;
import com.primetech.poc.ofa_api.features.sync.domain.CouchDbCheckpoint;
import com.primetech.poc.ofa_api.features.sync.infrastructure.CouchDbCheckpointRepository;
import com.primetech.poc.ofa_api.features.sync.infrastructure.CouchDbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
public class SyncService {

  private static final Logger log = LoggerFactory.getLogger(SyncService.class);
  private static final String CHECKPOINT_ID = "properties";

  private final CouchDbClient couchDbClient;
  private final CouchDbCheckpointRepository checkpointRepository;
  private final PropertySyncService propertySyncService;
  private final SyncProperties props;

  private volatile boolean bootstrapped = false;

  public SyncService(CouchDbClient couchDbClient,
                     CouchDbCheckpointRepository checkpointRepository,
                     PropertySyncService propertySyncService,
                     SyncProperties props) {
    this.couchDbClient = couchDbClient;
    this.checkpointRepository = checkpointRepository;
    this.propertySyncService = propertySyncService;
    this.props = props;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void onStartup() {
    if (!props.enabled()) {
      log.info("[sync] ETL sync disabled");
      return;
    }

    CouchDbCheckpoint checkpoint = checkpointRepository.findById(CHECKPOINT_ID).orElse(null);

    if (checkpoint == null) {
      log.info("[sync] No checkpoint found — running bootstrap");
      bootstrap();
    } else {
      log.info("[sync] Resuming from checkpoint last_seq={}", checkpoint.getLastSeq());
      bootstrapped = true;
    }
  }

  private void bootstrap() {
    List<JsonNode> allDocs = couchDbClient.getAllDocs();
    log.info("[sync] Bootstrap: found {} documents in CouchDB", allDocs.size());

    for (JsonNode doc : allDocs) {
      try {
        propertySyncService.syncDocument(doc);
      } catch (Exception e) {
        log.error("[sync] Bootstrap failed for doc {}: {}",
            doc.path("_id").asText(), e.getMessage(), e);
      }
    }

    String updateSeq = couchDbClient.getUpdateSeq();
    saveCheckpoint(updateSeq);
    bootstrapped = true;
    log.info("[sync] Bootstrap complete — checkpoint saved at seq={}", updateSeq);
  }

  @Scheduled(fixedDelayString = "${ofa.sync.poll-interval:5000}")
  @Transactional
  public void pollChanges() {
    if (!props.enabled() || !bootstrapped) {
      return;
    }

    String sinceSeq = checkpointRepository.findById(CHECKPOINT_ID)
        .map(CouchDbCheckpoint::getLastSeq)
        .orElse("0");

    CouchDbClient.ChangesResponse changes = couchDbClient.getChanges(sinceSeq);

    if (changes.docs().isEmpty()) {
      return;
    }

    log.info("[sync] Processing {} change(s) since seq={}", changes.docs().size(), sinceSeq);

    for (JsonNode doc : changes.docs()) {
      try {
        propertySyncService.syncDocument(doc);
      } catch (Exception e) {
        log.error("[sync] Failed to sync doc {}: {}",
            doc.path("_id").asText(), e.getMessage(), e);
      }
    }

    saveCheckpoint(changes.lastSeq());
  }

  private void saveCheckpoint(String lastSeq) {
    CouchDbCheckpoint checkpoint = checkpointRepository.findById(CHECKPOINT_ID)
        .orElseGet(() -> new CouchDbCheckpoint(CHECKPOINT_ID, lastSeq));

    if (!checkpoint.getId().equals(CHECKPOINT_ID)) {
      checkpoint = new CouchDbCheckpoint(CHECKPOINT_ID, lastSeq);
    }

    checkpoint.setLastSeq(lastSeq);
    checkpointRepository.save(checkpoint);
  }
}
