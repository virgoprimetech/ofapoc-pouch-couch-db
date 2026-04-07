package com.primetech.poc.identity.features.assignment.assign;

import com.github.f4b6a3.uuid.UuidCreator;
import com.primetech.poc.identity.features.assignment.domain.UserRoleAssignment;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentEntity;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentJpaMapper;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentMapper;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentRepository;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.domain.RoleScope;
import com.primetech.poc.identity.features.role.infrastructure.RoleEntity;
import com.primetech.poc.identity.features.role.infrastructure.RoleJpaMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleRepository;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.infrastructure.UserEntity;
import com.primetech.poc.identity.features.user.infrastructure.UserJpaMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserRepository;
import com.primetech.poc.identity.shared.exception.BusinessException;
import com.primetech.poc.identity.shared.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Assign Role Handler
 */
@Component
public class AssignRoleHandler {

  private static final Logger log = LoggerFactory.getLogger(AssignRoleHandler.class);

  private final UserRoleAssignmentRepository assignmentRepository;
  private final UserRoleAssignmentJpaMapper assignmentJpaMapper;
  private final UserRoleAssignmentMapper assignmentMapper;
  private final UserRepository userRepository;
  private final UserJpaMapper userJpaMapper;
  private final RoleRepository roleRepository;
  private final RoleJpaMapper roleJpaMapper;

  public AssignRoleHandler(
      UserRoleAssignmentRepository assignmentRepository,
      UserRoleAssignmentJpaMapper assignmentJpaMapper,
      UserRoleAssignmentMapper assignmentMapper,
      UserRepository userRepository,
      UserJpaMapper userJpaMapper,
      RoleRepository roleRepository,
      RoleJpaMapper roleJpaMapper) {
    this.assignmentRepository = assignmentRepository;
    this.assignmentJpaMapper = assignmentJpaMapper;
    this.assignmentMapper = assignmentMapper;
    this.userRepository = userRepository;
    this.userJpaMapper = userJpaMapper;
    this.roleRepository = roleRepository;
    this.roleJpaMapper = roleJpaMapper;
  }

  @Transactional
  public UserRoleAssignment handle(AssignRoleCommand command) {
    log.debug("Assigning role {} to user {}", command.roleId(), command.userId());

    // Validate user exists
    UserEntity userEntity = userRepository.findById(command.userId())
        .orElseThrow(() -> new EntityNotFoundException("User", command.userId()));
    User user = userJpaMapper.toDomain(userEntity);

    if (!user.isActive()) {
      throw new BusinessException("USER_NOT_ACTIVE", "Cannot assign roles to inactive users");
    }

    // Validate role exists
    RoleEntity roleEntity = roleRepository.findById(command.roleId())
        .orElseThrow(() -> new EntityNotFoundException("Role", command.roleId()));
    Role role = roleJpaMapper.toDomain(roleEntity);

    // Validate scope requirements
    validateScopeRequirements(role, command);

    // Check for existing assignment
    var existing = assignmentRepository.findByUserIdAndRoleIdAndPropertyId(
        command.userId(), command.roleId(), command.propertyId()
    );
    if (existing.isPresent()) {
      throw new BusinessException("ASSIGNMENT_EXISTS", "This role assignment already exists");
    }

    Instant now = Instant.now();
    UUID id = UuidCreator.getTimeOrderedEpoch();

    // Set default validFrom if not provided
    Instant validFrom = command.validFrom() != null ? command.validFrom() : now;

    // Create assignment with validFrom
    UserRoleAssignment assignment = new UserRoleAssignment(
        id,
        command.userId(),
        command.roleId(),
        command.propertyId(),
        command.chainId(),
        validFrom,
        command.validUntil(),
        now,
        now,
        null
    );

    UserRoleAssignmentEntity entity = assignmentJpaMapper.toEntity(assignment);
    UserRoleAssignmentEntity saved = assignmentRepository.save(entity);

    log.info("Role assigned: assignmentId={}, userId={}, roleId={}", saved.getId(), command.userId(), command.roleId());

    return assignmentJpaMapper.toDomain(saved);
  }

  private void validateScopeRequirements(Role role, AssignRoleCommand command) {
    if (role.scope() == RoleScope.PROPERTY && command.propertyId() == null) {
      throw new BusinessException("PROPERTY_ID_REQUIRED",
          "Property-scoped roles require a propertyId");
    }
    if (role.scope() == RoleScope.CHAIN && command.chainId() == null) {
      throw new BusinessException("CHAIN_ID_REQUIRED",
          "Chain-scoped roles require a chainId");
    }
    if (role.scope() == RoleScope.GLOBAL && (command.propertyId() != null || command.chainId() != null)) {
      throw new BusinessException("GLOBAL_ROLE_NO_SCOPE",
          "Global roles should not have propertyId or chainId");
    }
  }
}
