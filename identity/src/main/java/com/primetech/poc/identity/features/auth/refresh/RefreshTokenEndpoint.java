package com.primetech.poc.identity.features.auth.refresh;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Refresh Endpoint
 */
@RestController
@RequestMapping("/auth")
class RefreshTokenEndpoint {

  private final RefreshTokenHandler handler;

  public RefreshTokenEndpoint(RefreshTokenHandler handler) {
    this.handler = handler;
  }

  @PostMapping("/refresh")
  public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    RefreshTokenResponse response = handler.handle(request.refreshToken());
    return ResponseEntity.ok(response);
  }
}
