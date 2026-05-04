package vn.com.primetech.ofagw.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.couchdb")
public class CouchdbProperties {
  private ProxyAuth proxyAuth;

  @Getter
  @Setter
  public static class ProxyAuth{
    private String secret;
    private String algorithm;
    private Charset encoding;
  }
}
