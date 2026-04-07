package com.primetech.poc.identity.features.auth.login;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login Endpoint
 */
@RestController
@RequestMapping("/auth")
class LoginEndpoint {

  private final LoginHandler handler;

  public LoginEndpoint(LoginHandler handler) {
    this.handler = handler;
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginCommand command = new LoginCommand(request.tenant(), request.username(), request.password());
    LoginResponse response = handler.handle(command);
    return ResponseEntity.ok(response);
  }
}
