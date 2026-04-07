package com.primetech.poc.identity.features.auth.logout;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logout Endpoint
 * <p>
 * Note: In a stateless JWT architecture, logout is typically handled client-side
 * by removing the tokens. This endpoint exists for API consistency and could be
 * extended to support token blacklisting if needed.
 */
@RestController
@RequestMapping("/auth")
class LogoutEndpoint {

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    // In a stateless JWT system, the client should discard the tokens
    // This endpoint exists for API consistency
    // Could be extended to support token blacklisting
    return ResponseEntity.noContent().build();
  }
}
