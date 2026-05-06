package com.primetech.poc.identity.shared.couchdb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;
import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "app.couchdb")
public class CouchDbProperties {

  private String url = "http://localhost:5984";
  private Duration connectTimeout = Duration.ofSeconds(5);
  private Duration readTimeout = Duration.ofSeconds(30);
  private final ProxyAuth proxyAuth = new ProxyAuth();

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }

  public ProxyAuth getProxyAuth() {
    return proxyAuth;
  }

  public static class ProxyAuth {
    private String username = "admin";
    private String roles = "_admin";
    private String secret = "change-me";
    private String algorithm = "HmacSHA1";
    private String encoding = "UTF-8";

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getRoles() {
      return roles;
    }

    public void setRoles(String roles) {
      this.roles = roles;
    }

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }

    public String getAlgorithm() {
      return algorithm;
    }

    public void setAlgorithm(String algorithm) {
      this.algorithm = algorithm;
    }

    public String getEncoding() {
      return encoding;
    }

    public void setEncoding(String encoding) {
      this.encoding = encoding;
    }

    public Charset charset() {
      return Charset.forName(encoding);
    }
  }
}
