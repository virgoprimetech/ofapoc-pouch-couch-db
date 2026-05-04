package vn.com.primetech.ofagw.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security Configuration
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    return http
        // 1. Disable CSRF for APIs (common in Gateway setups)
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        // 2. Allow all traffic through the gate
        .authorizeExchange(exchanges -> exchanges
            .anyExchange().permitAll()
        )
        // 3. Still attempt to decode JWTs if the Authorization header is present
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(Customizer.withDefaults())
        )
        .build();
  }
}
