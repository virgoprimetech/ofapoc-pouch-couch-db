package com.primetech.poc.identity.features.assignment.list;

import com.primetech.poc.identity.features.assignment.assign.AssignmentResponse;
import com.primetech.poc.identity.features.assignment.domain.UserRoleAssignment;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentMapper;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleJpaMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * List User Assignments Endpoint
 */
@RestController
@RequestMapping("/assignments")
class ListUserAssignmentsEndpoint {

  private final ListUserAssignmentsHandler handler;
  private final UserRoleAssignmentMapper assignmentMapper;
  private final RoleRepository roleRepository;
  private final RoleJpaMapper roleJpaMapper;

  public ListUserAssignmentsEndpoint(
      ListUserAssignmentsHandler handler,
      UserRoleAssignmentMapper assignmentMapper,
      RoleRepository roleRepository,
      RoleJpaMapper roleJpaMapper) {
    this.handler = handler;
    this.assignmentMapper = assignmentMapper;
    this.roleRepository = roleRepository;
    this.roleJpaMapper = roleJpaMapper;
  }

  @GetMapping("/user/{userId}")
  public ResponseEntity<List<AssignmentResponse>> listByUser(
      @PathVariable UUID userId,
      @RequestParam(required = false, defaultValue = "false") boolean validOnly
  ) {
    ListUserAssignmentsQuery query = new ListUserAssignmentsQuery(userId, validOnly);
    List<UserRoleAssignment> assignments = handler.handle(query);

    // Get role info for all assignments
    Map<UUID, Role> roleMap = roleRepository.findAllById(
        assignments.stream().map(UserRoleAssignment::roleId).toList()
    ).stream().map(roleJpaMapper::toDomain).collect(Collectors.toMap(Role::id, Function.identity()));

    List<AssignmentResponse> response = assignments.stream()
        .map(a -> {
          Role role = roleMap.get(a.roleId());
          return new AssignmentResponse(
              a.id(),
              a.userId(),
              a.roleId(),
              role != null ? role.code() : null,
              role != null ? role.displayName() : null,
              a.propertyId(),
              a.chainId(),
              a.validFrom(),
              a.validUntil(),
              a.createdAt(),
              a.updatedAt()
          );
        })
        .toList();

    return ResponseEntity.ok(response);
  }
}
