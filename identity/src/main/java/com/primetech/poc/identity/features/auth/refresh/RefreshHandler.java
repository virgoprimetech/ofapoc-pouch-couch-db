package com.primetech.poc.identity.features.auth.refresh;

import com.primetech.poc.identity.features.assignment.domain.UserRoleAssignment;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentRepository;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleJpaMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleRepository;
import com.primetech.poc.identity.features.tenant.infrastructure.TenantEntity;
import com.primetech.poc.identity.features.tenant.infrastructure.TenantRepository;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.domain.UserStatus;
import com.primetech.poc.identity.features.user.infrastructure.UserJpaMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserRepository;
import com.primetech.poc.identity.shared.exception.AuthenticationException;
import com.primetech.poc.identity.shared.multitenancy.TenantContext;
import com.primetech.poc.identity.shared.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Refresh Handler
 */
@Component
public class RefreshHandler {

  private static final Logger log = LoggerFactory.getLogger(RefreshHandler.class);

  private final JwtTokenProvider jwtTokenProvider;
  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final UserJpaMapper userJpaMapper;
  private final RoleRepository roleRepository;
  private final RoleJpaMapper roleJpaMapper;
  private final UserRoleAssignmentRepository assignmentRepository;

  public RefreshHandler(
      JwtTokenProvider jwtTokenProvider,
      TenantRepository tenantRepository,
      UserRepository userRepository,
      UserJpaMapper userJpaMapper,
      RoleRepository roleRepository,
      RoleJpaMapper roleJpaMapper,
      UserRoleAssignmentRepository assignmentRepository) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.userJpaMapper = userJpaMapper;
    this.roleRepository = roleRepository;
    this.roleJpaMapper = roleJpaMapper;
    this.assignmentRepository = assignmentRepository;
  }

  @Transactional(readOnly = true)
  public RefreshResponse handle(String refreshToken) {
    log.debug("Refresh token request");

    // Validate refresh token
    if (!jwtTokenProvider.validateToken(refreshToken)) {
      throw new AuthenticationException("Invalid refresh token");
    }

    if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
      throw new AuthenticationException("Token is not a refresh token");
    }

    String tenantCode = jwtTokenProvider.getTenantCode(refreshToken);
    UUID userId = jwtTokenProvider.getUserId(refreshToken);

    try {
      // Validate tenant exists and is active (in public schema)
      TenantEntity tenant = tenantRepository.findByCode(tenantCode)
          .orElseThrow(() -> new AuthenticationException("Tenant not found"));

      if (!tenant.isActive()) {
        throw new AuthenticationException("Tenant is not active");
      }

      // Set tenant context for subsequent queries
      TenantContext.setTenant(tenant.getCode(), tenant.getId(), tenant.getSchemaName());

      // Get user
      User user = userRepository.findById(userId)
          .map(userJpaMapper::toDomain)
          .orElseThrow(() -> new AuthenticationException("User not found"));

      // Check if user is active
      if (user.status() != UserStatus.ACTIVE) {
        throw new AuthenticationException("User account is not active");
      }

      // Get user's valid role assignments
      List<UserRoleAssignment> assignments = assignmentRepository.findValidByUserId(user.id())
          .stream()
          .map(entity -> new UserRoleAssignment(
              entity.getId(), entity.getUserId(), entity.getRoleId(),
              entity.getPropertyId(), entity.getChainId(),
              entity.getValidFrom(), entity.getValidUntil(),
              entity.getCreatedAt(), entity.getUpdatedAt(), entity.getDeletedAt()
          ))
          .filter(a -> a.isCurrentlyValid())
          .toList();

      // Get role information
      List<UUID> roleIds = assignments.stream()
          .map(UserRoleAssignment::roleId)
          .distinct()
          .toList();

      Map<UUID, Role> roleMap = roleRepository.findAllById(roleIds)
          .stream()
          .map(roleJpaMapper::toDomain)
          .collect(Collectors.toMap(Role::id, Function.identity()));

      // Build role codes list
      List<String> roleCodes = assignments.stream()
          .map(a -> roleMap.get(a.roleId()))
          .filter(Objects::nonNull)
          .map(Role::code)
          .distinct()
          .toList();

      // Build property IDs list
      List<String> propertyIds = assignments.stream()
          .filter(a -> a.propertyId() != null)
          .map(a -> a.propertyId().toString())
          .distinct()
          .toList();

      // Generate new tokens with tenant_code
      String newAccessToken = jwtTokenProvider.generateAccessToken(
          user.id(),
          user.username().value(),
          user.email().value(),
          roleCodes,
          propertyIds
      );

      String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.id());

      log.info("Token refreshed for tenant: {}, user: {}", tenant.getCode(), user.id());

      return new RefreshResponse(
          newAccessToken,
          newRefreshToken,
          "Bearer",
          jwtTokenProvider.getAccessTokenExpirationMs() / 1000,
          tenant.getId(),
          user.id(),
          user.email().value(),
          roleCodes
      );
    } finally {
      // Always clear tenant context
      TenantContext.clear();
    }
  }
}
