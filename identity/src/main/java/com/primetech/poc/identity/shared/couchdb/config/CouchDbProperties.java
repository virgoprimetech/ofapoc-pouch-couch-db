package com.primetech.poc.identity.shared.couchdb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.couchdb")
@Getter
@Setter
public class CouchDbProperties {

  private String url = "http://localhost:5984";
  private Duration connectTimeout = Duration.ofSeconds(5);
  private Duration readTimeout = Duration.ofSeconds(30);
  private final ProxyAuth proxyAuth = new ProxyAuth();
  private List<String> scopedDbs = new ArrayList<>();

  @Setter
  @Getter
  public static class ProxyAuth {
    private String username = "admin";
    private String roles = "_admin";
    private String secret = "change-me";
    private String algorithm = "HmacSHA1";
    private String encoding = "UTF-8";

    public Charset charset() {
      return Charset.forName(encoding);
    }
  }
}
