package com.primetech.poc.identity.features.role.delete;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Delete Role Endpoint
 */
@RestController
@RequestMapping("/roles")
class DeleteRoleEndpoint {

  private final DeleteRoleHandler handler;

  public DeleteRoleEndpoint(DeleteRoleHandler handler) {
    this.handler = handler;
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    DeleteRoleCommand command = new DeleteRoleCommand(id);
    handler.handle(command);
    return ResponseEntity.noContent().build();
  }
}
