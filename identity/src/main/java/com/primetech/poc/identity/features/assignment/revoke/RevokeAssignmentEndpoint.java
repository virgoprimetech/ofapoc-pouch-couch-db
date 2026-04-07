package com.primetech.poc.identity.features.assignment.revoke;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Revoke Assignment Endpoint
 */
@RestController
@RequestMapping("/assignments")
class RevokeAssignmentEndpoint {

  private final RevokeAssignmentHandler handler;

  public RevokeAssignmentEndpoint(RevokeAssignmentHandler handler) {
    this.handler = handler;
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> revoke(@PathVariable UUID id) {
    RevokeAssignmentCommand command = new RevokeAssignmentCommand(id);
    handler.handle(command);
    return ResponseEntity.noContent().build();
  }
}
