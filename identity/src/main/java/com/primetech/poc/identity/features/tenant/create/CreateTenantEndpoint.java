package com.primetech.poc.identity.features.tenant.create;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Create Tenant Endpoint
 */
@RestController
@RequestMapping("/tenants")
class CreateTenantEndpoint {

  private final CreateTenantHandler handler;

  public CreateTenantEndpoint(CreateTenantHandler handler) {
    this.handler = handler;
  }

  @PostMapping
  public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
    CreateTenantCommand command = new CreateTenantCommand(
        request.code(),
        request.displayName(),
        request.adminUsername(),
        request.adminEmail(),
        request.adminPassword()
    );
    TenantResponse response = handler.handle(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
