package com.primetech.poc.identity.features.permission.list;

import com.primetech.poc.identity.features.permission.domain.Permission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * List Permissions Endpoint
 */
@RestController
@RequestMapping("/permissions")
class ListPermissionsEndpoint {

  private final ListPermissionsHandler handler;
  private final PermissionMapper permissionMapper;

  public ListPermissionsEndpoint(ListPermissionsHandler handler, PermissionMapper permissionMapper) {
    this.handler = handler;
    this.permissionMapper = permissionMapper;
  }

  @GetMapping
  public ResponseEntity<List<PermissionResponse>> list(@RequestParam(required = false) String resource) {
    ListPermissionsQuery query = new ListPermissionsQuery(resource);
    List<Permission> permissions = handler.handle(query);
    return ResponseEntity.ok(permissionMapper.toResponseList(permissions));
  }
}
