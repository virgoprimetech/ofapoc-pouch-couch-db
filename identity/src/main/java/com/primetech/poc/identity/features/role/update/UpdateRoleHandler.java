package com.primetech.poc.identity.features.role.update;

import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleEntity;
import com.primetech.poc.identity.features.role.infrastructure.RoleJpaMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleRepository;
import com.primetech.poc.identity.shared.exception.BusinessException;
import com.primetech.poc.identity.shared.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Update Role Handler
 */
@Component
public class UpdateRoleHandler {

  private static final Logger log = LoggerFactory.getLogger(UpdateRoleHandler.class);

  private final RoleRepository repository;
  private final RoleJpaMapper jpaMapper;
  private final RoleMapper roleMapper;

  public UpdateRoleHandler(
      RoleRepository repository,
      RoleJpaMapper jpaMapper,
      RoleMapper roleMapper) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
    this.roleMapper = roleMapper;
  }

  @Transactional
  public Role handle(UpdateRoleCommand command) {
    log.debug("Updating role: {}", command.id());

    RoleEntity entity = repository.findById(command.id())
        .orElseThrow(() -> new EntityNotFoundException("Role", command.id()));

    Role existing = jpaMapper.toDomain(entity);

    if (existing.isSystem()) {
      throw new BusinessException("CANNOT_UPDATE_SYSTEM_ROLE", "System roles cannot be modified");
    }

    Instant now = Instant.now();
    Role updated = roleMapper.mergeForUpdate(command, existing, now);

    jpaMapper.updateEntityFromDomain(updated, entity);
    entity.setUpdatedAt(now);

    RoleEntity saved = repository.save(entity);

    log.info("Role updated: {}", saved.getId());

    return jpaMapper.toDomain(saved);
  }
}
