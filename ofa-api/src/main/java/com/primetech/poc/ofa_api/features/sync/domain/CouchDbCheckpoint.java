package com.primetech.poc.ofa_api.features.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "couchdb_sync_checkpoint")
public class CouchDbCheckpoint {

  @Id
  private String id;

  @Column(name = "last_seq", nullable = false)
  private String lastSeq;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CouchDbCheckpoint() {
    // JPA
  }

  public CouchDbCheckpoint(String id, String lastSeq) {
    this.id = id;
    this.lastSeq = lastSeq;
    this.updatedAt = Instant.now();
  }

  public void setLastSeq(String lastSeq) {
    this.lastSeq = lastSeq;
    this.updatedAt = Instant.now();
  }

}
