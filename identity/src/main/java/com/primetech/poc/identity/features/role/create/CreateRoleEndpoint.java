package com.primetech.poc.identity.features.role.create;

import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Create Role Endpoint
 */
@RestController
@RequestMapping("/roles")
class CreateRoleEndpoint {

  private final CreateRoleHandler handler;
  private final RoleMapper roleMapper;

  public CreateRoleEndpoint(CreateRoleHandler handler, RoleMapper roleMapper) {
    this.handler = handler;
    this.roleMapper = roleMapper;
  }

  @PostMapping
  public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
    CreateRoleCommand command = roleMapper.toCommand(request);
    Role role = handler.handle(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(roleMapper.toResponse(role));
  }
}
