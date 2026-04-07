package com.primetech.poc.identity.features.role.create;

import com.github.f4b6a3.uuid.UuidCreator;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleEntity;
import com.primetech.poc.identity.features.role.infrastructure.RoleJpaMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleRepository;
import com.primetech.poc.identity.shared.exception.DuplicateEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

/**
 * Create Role Handler
 */
@Component
public class CreateRoleHandler {

  private static final Logger log = LoggerFactory.getLogger(CreateRoleHandler.class);

  private final RoleRepository repository;
  private final RoleJpaMapper jpaMapper;
  private final RoleMapper roleMapper;

  public CreateRoleHandler(
      RoleRepository repository,
      RoleJpaMapper jpaMapper,
      RoleMapper roleMapper) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
    this.roleMapper = roleMapper;
  }

  @Transactional
  public Role handle(CreateRoleCommand command) {
    log.debug("Creating role with code: {}", command.code());

    if (repository.existsByCode(command.code())) {
      throw new DuplicateEntityException("Role", "code", command.code());
    }

    Instant now = Instant.now();
    UUID id = UuidCreator.getTimeOrderedEpoch();

    Role role = roleMapper.toDomain(command, id, now);
    if (role.permissionIds() == null) {
      role = new Role(
          role.id(), role.code(), role.displayName(), role.scope(),
          role.isSystem(), role.description(), new HashSet<>(),
          role.createdAt(), role.updatedAt(), role.deletedAt()
      );
    }

    RoleEntity entity = jpaMapper.toEntity(role);
    RoleEntity saved = repository.save(entity);

    log.info("Role created: {}", saved.getId());

    return jpaMapper.toDomain(saved);
  }
}
