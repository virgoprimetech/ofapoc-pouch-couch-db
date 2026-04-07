package com.primetech.poc.identity.features.role.update;

import com.primetech.poc.identity.features.role.create.RoleResponse;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Update Role Endpoint
 */
@RestController
@RequestMapping("/roles")
class UpdateRoleEndpoint {

  private final UpdateRoleHandler handler;
  private final RoleMapper roleMapper;

  public UpdateRoleEndpoint(UpdateRoleHandler handler, RoleMapper roleMapper) {
    this.handler = handler;
    this.roleMapper = roleMapper;
  }

  @PutMapping("/{id}")
  public ResponseEntity<RoleResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRoleRequest request
  ) {
    UpdateRoleCommand command = roleMapper.toCommand(request, id);
    Role role = handler.handle(command);
    return ResponseEntity.ok(roleMapper.toResponse(role));
  }
}
