package com.primetech.poc.identity.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

  private String allowedOrigins = "http://localhost:3000";

  public String getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(String allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  public List<String> getAllowedOriginsList() {
    return Arrays.asList(allowedOrigins.split(","));
  }
}
