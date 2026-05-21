package com.primetech.poc.identity.shared.couchdb.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

@Component
public class CouchDbProxyAuthRequestInterceptor implements RequestInterceptor {

  private final CouchDbProperties couchDbProperties;

  public CouchDbProxyAuthRequestInterceptor(CouchDbProperties couchDbProperties) {
    this.couchDbProperties = couchDbProperties;
  }

  @Override
  public void apply(RequestTemplate template) {
    CouchDbProperties.ProxyAuth proxyAuth = couchDbProperties.getProxyAuth();
    String username = proxyAuth.getUsername();
    template.header("X-Auth-CouchDB-UserName", username);
    template.header("X-Auth-CouchDB-Roles", proxyAuth.getRoles());
    template.header("X-Auth-CouchDB-Token", sign(username, proxyAuth));
  }

  private String sign(String username, CouchDbProperties.ProxyAuth proxyAuth) {
    try {
      Mac mac = Mac.getInstance(proxyAuth.getAlgorithm());
      SecretKeySpec keySpec = new SecretKeySpec(
          proxyAuth.getSecret().getBytes(proxyAuth.charset()),
          proxyAuth.getAlgorithm());
      mac.init(keySpec);
      byte[] token = mac.doFinal(username.getBytes(proxyAuth.charset()));
      return HexFormat.of().formatHex(token);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to create CouchDB proxy auth token", exception);
    }
  }
}
