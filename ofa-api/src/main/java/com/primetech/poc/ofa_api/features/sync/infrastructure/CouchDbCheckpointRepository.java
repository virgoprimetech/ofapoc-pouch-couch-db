package com.primetech.poc.ofa_api.features.sync.infrastructure;

import com.primetech.poc.ofa_api.features.sync.domain.CouchDbCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouchDbCheckpointRepository extends JpaRepository<CouchDbCheckpoint, String> {
}
