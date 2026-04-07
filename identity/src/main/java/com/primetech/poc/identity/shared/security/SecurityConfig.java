package com.primetech.poc.identity.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security Configuration
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .authorizeHttpRequests(auth -> auth
            // Public endpoints
            .requestMatchers("/auth/**").permitAll()
            // OAuth2 and OIDC discovery endpoints (public for clients to auto-configure)
            .requestMatchers("/.well-known/jwks.json").permitAll()
            .requestMatchers("/.well-known/openid-configuration").permitAll()
            .requestMatchers("/oauth2/**").permitAll()
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            // Swagger UI and OpenAPI docs
            .requestMatchers("/swagger-ui/**").permitAll()
            .requestMatchers("/swagger-ui.html").permitAll()
            .requestMatchers("/v3/api-docs/**").permitAll()
            .requestMatchers("/api-docs/**").permitAll()
            .requestMatchers("/webjars/**").permitAll()
            .requestMatchers("/swagger-resources/**").permitAll()
            .requestMatchers("/configuration/**").permitAll()
//            .requestMatchers("/users/**").permitAll()
            // All other endpoints require authentication
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
