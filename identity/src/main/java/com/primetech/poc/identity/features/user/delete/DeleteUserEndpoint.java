package com.primetech.poc.identity.features.user.delete;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Delete User Endpoint
 * <p>
 * REST API endpoint for deleting users.
 */
@RestController
@RequestMapping("/users")
class DeleteUserEndpoint {

  private final DeleteUserHandler handler;

  public DeleteUserEndpoint(DeleteUserHandler handler) {
    this.handler = handler;
  }

  /**
   * Delete a user (soft delete)
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    DeleteUserCommand command = new DeleteUserCommand(id);
    handler.handle(command);
    return ResponseEntity.noContent().build();
  }
}
