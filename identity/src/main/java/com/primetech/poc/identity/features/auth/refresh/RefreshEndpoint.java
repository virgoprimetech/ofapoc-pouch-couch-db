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
class RefreshEndpoint {

  private final RefreshHandler handler;

  public RefreshEndpoint(RefreshHandler handler) {
    this.handler = handler;
  }

  @PostMapping("/refresh")
  public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    RefreshResponse response = handler.handle(request.refreshToken());
    return ResponseEntity.ok(response);
  }
}
