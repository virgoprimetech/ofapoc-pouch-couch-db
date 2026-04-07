package com.primetech.poc.identity.features.auth.register;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Register Endpoint
 */
@RestController
@RequestMapping("/auth")
class RegisterEndpoint {

  private final RegisterHandler handler;

  public RegisterEndpoint(RegisterHandler handler) {
    this.handler = handler;
  }

  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    RegisterCommand command = new RegisterCommand(
        request.username(),
        request.email(),
        request.password(),
        request.firstName(),
        request.lastName()
    );
    RegisterResponse response = handler.handle(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
