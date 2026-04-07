package com.primetech.poc.identity.features.auth.login.chain;

import com.primetech.poc.identity.features.assignment.domain.UserRoleAssignment;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentRepository;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleJpaMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleRepository;
import com.primetech.poc.identity.features.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the user roles and property assignments.
 *
 * <p>This processor runs after user authentication and queries the tenant schema
 * to resolve role assignments and role details.
 *
 * <p>Steps:
 * <ol>
 *   <li>Get user valid role assignments</li>
 *   <li>Load role details for each assignment</li>
 *   <li>Build role codes list for JWT claims</li>
 *   <li>Build property IDs list for property-scoped roles</li>
 * </ol>
 */
@Component
public class RoleResolutionProcessor implements LoginProcessor {

    @Override
    public int getOrder() {
        return 30;
    }

    private static final Logger log = LoggerFactory.getLogger(RoleResolutionProcessor.class);

    private final UserRoleAssignmentRepository assignmentRepository;
    private final RoleRepository roleRepository;
    private final RoleJpaMapper roleJpaMapper;

    public RoleResolutionProcessor(
            UserRoleAssignmentRepository assignmentRepository,
            RoleRepository roleRepository,
            RoleJpaMapper roleJpaMapper) {
        this.assignmentRepository = assignmentRepository;
        this.roleRepository = roleRepository;
        this.roleJpaMapper = roleJpaMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public void process(LoginContext context, LoginChain chain) {
        User user = context.getUser();
        log.debug("Resolving roles for user: userId={}", user.id());

        // Step 1: Get user valid role assignments
        List<UserRoleAssignment> assignments = assignmentRepository.findValidByUserId(user.id())
                .stream()
                .map(assignment -> {
                    var entity = assignmentRepository.findById(assignment.getId());
                    return entity.map(e -> new UserRoleAssignment(
                            e.getId(),
                            e.getUserId(),
                            e.getRoleId(),
                            e.getPropertyId(),
                            e.getChainId(),
                            e.getValidFrom(),
                            e.getValidUntil(),
                            e.getCreatedAt(),
                            e.getUpdatedAt(),
                            e.getDeletedAt()
                    )).orElse(null);
                })
                .filter(a -> a != null && a.isCurrentlyValid())
                .toList();

        context.setAssignments(assignments);

        // Step 2: Get role information
        List<UUID> roleIds = assignments.stream()
                .map(UserRoleAssignment::roleId)
                .distinct()
                .toList();

        Map<UUID, Role> roleMap = roleRepository.findAllById(roleIds)
                .stream()
                .map(roleJpaMapper::toDomain)
                .collect(Collectors.toMap(Role::id, Function.identity()));

        context.setRoleMap(roleMap);

        // Step 3: Build role codes list
        List<String> roleCodes = assignments.stream()
                .map(a -> roleMap.get(a.roleId()))
                .filter(Objects::nonNull)
                .map(Role::code)
                .distinct()
                .toList();

        context.setRoleCodes(roleCodes);

        // Step 4: Build property IDs list for property-scoped roles
        List<String> propertyIds = assignments.stream()
                .filter(a -> a.propertyId() != null)
                .map(a -> a.propertyId().toString())
                .distinct()
                .toList();

        context.setPropertyIds(propertyIds);

        log.debug("Roles resolved for user: userId={}, roles={}, propertyCount={}",
                user.id(), roleCodes, propertyIds.size());

        // Continue to next processor
        chain.process(context);
    }
}
