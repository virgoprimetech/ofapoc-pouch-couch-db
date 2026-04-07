package com.primetech.poc.identity.features.role.list;

import com.primetech.poc.identity.features.role.create.RoleResponse;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.domain.RoleScope;
import com.primetech.poc.identity.features.role.infrastructure.RoleMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * List Roles Endpoint
 */
@RestController
@RequestMapping("/roles")
class ListRolesEndpoint {

  private final ListRolesHandler handler;
  private final RoleMapper roleMapper;

  public ListRolesEndpoint(ListRolesHandler handler, RoleMapper roleMapper) {
    this.handler = handler;
    this.roleMapper = roleMapper;
  }

  @GetMapping
  public ResponseEntity<List<RoleResponse>> list(@RequestParam(required = false) RoleScope scope) {
    ListRolesQuery query = new ListRolesQuery(scope);
    List<Role> roles = handler.handle(query);
    return ResponseEntity.ok(roleMapper.toResponseList(roles));
  }
}
