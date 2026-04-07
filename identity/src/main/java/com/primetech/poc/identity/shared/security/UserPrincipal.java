package com.primetech.poc.identity.shared.security;

import org.springframework.security.core.GrantedAuthority;

import java.security.Principal;

import java.util.Collection;
import java.util.UUID;

/**
 * User Principal for Spring Security
 */
public record UserPrincipal(
    String tenantCode,
    UUID userId,
    Collection<? extends GrantedAuthority> authorities
) implements Principal {

  @Override
  public String getName() {
    return userId.toString();
  }

  /**
   * Check if user has a specific role
   */
  public boolean hasRole(String role) {
    return authorities.stream()
        .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + role));
  }

  /**
   * Check if user has any of the specified roles
   */
  public boolean hasAnyRole(String... roles) {
    for (String role : roles) {
      if (hasRole(role)) {
        return true;
      }
    }
    return false;
  }
}
