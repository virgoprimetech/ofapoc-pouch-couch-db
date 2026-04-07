package com.primetech.poc.identity.shared.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT configuration properties.
 * Note: Secret key is no longer stored here - RSA keys are used for signing.
 */
@Setter
@Getter
@Configuration
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

  private String issuer;
  private String audience;
  private long accessTokenExpirationMinutes = 15;
  private long refreshTokenExpirationDays = 7;

  public long getAccessTokenExpirationMs() {
    return accessTokenExpirationMinutes * 60 * 1000;
  }

  public long getRefreshTokenExpirationMs() {
    return refreshTokenExpirationDays * 24 * 60 * 60 * 1000;
  }
}
