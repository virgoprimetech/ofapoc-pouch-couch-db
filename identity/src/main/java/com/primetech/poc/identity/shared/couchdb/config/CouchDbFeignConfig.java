package com.primetech.poc.identity.shared.couchdb.config;

import feign.RequestInterceptor;
import feign.Request.Options;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CouchDbFeignConfig {

  @Bean
  RequestInterceptor couchDbRequestInterceptor(CouchDbProxyAuthRequestInterceptor interceptor) {
    return interceptor;
  }

  @Bean
  Options couchDbFeignOptions(CouchDbProperties properties) {
    return new Options(
        properties.getConnectTimeout(),
        properties.getReadTimeout(),
        true);
  }
}
