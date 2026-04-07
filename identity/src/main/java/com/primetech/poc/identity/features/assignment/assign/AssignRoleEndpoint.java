package com.primetech.poc.identity.features.assignment.assign;

import com.primetech.poc.identity.features.assignment.domain.UserRoleAssignment;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentMapper;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleJpaMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assign Role Endpoint
 */
@RestController
@RequestMapping("/assignments")
@RequiredArgsConstructor
class AssignRoleEndpoint {

  private final AssignRoleHandler handler;
  private final UserRoleAssignmentMapper assignmentMapper;
  private final RoleRepository roleRepository;
  private final RoleJpaMapper roleJpaMapper;

  @PostMapping
  public ResponseEntity<AssignmentResponse> assign(@Valid @RequestBody AssignRoleRequest request) {
    AssignRoleCommand command = assignmentMapper.toCommand(request);
    UserRoleAssignment assignment = handler.handle(command);

    // Get role info for response
    Role role = roleRepository.findById(assignment.roleId())
        .map(roleJpaMapper::toDomain)
        .orElse(null);

    AssignmentResponse response = new AssignmentResponse(
        assignment.id(),
        assignment.userId(),
        assignment.roleId(),
        role != null ? role.code() : null,
        role != null ? role.displayName() : null,
        assignment.propertyId(),
        assignment.chainId(),
        assignment.validFrom(),
        assignment.validUntil(),
        assignment.createdAt(),
        assignment.updatedAt()
    );

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
