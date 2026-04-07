package com.primetech.poc.identity.features.role.get;

import com.primetech.poc.identity.features.role.create.RoleResponse;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Get Role Endpoint
 */
@RestController
@RequestMapping("/roles")
class GetRoleEndpoint {

  private final GetRoleHandler handler;
  private final RoleMapper roleMapper;

  public GetRoleEndpoint(GetRoleHandler handler, RoleMapper roleMapper) {
    this.handler = handler;
    this.roleMapper = roleMapper;
  }

  @GetMapping("/{id}")
  public ResponseEntity<RoleResponse> get(@PathVariable UUID id) {
    GetRoleQuery query = new GetRoleQuery(id);
    Role role = handler.handle(query);
    return ResponseEntity.ok(roleMapper.toResponse(role));
  }
}
