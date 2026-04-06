package com.primetech.poc.ofa_api.features.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ofa.sync")
public record SyncProperties(
    boolean enabled,
    CouchDb couchdb,
    long pollInterval
) {
  public record CouchDb(String url, String database) {
  }
}
