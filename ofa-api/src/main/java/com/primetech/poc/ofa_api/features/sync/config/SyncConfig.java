package com.primetech.poc.ofa_api.features.sync.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SyncProperties.class)
public class SyncConfig {

  @Bean
  public RestClient couchDbRestClient(SyncProperties props) {
    return RestClient.builder()
        .baseUrl(props.couchdb().url())
        .build();
  }
}
