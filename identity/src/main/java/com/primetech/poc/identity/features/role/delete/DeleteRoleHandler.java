package com.primetech.poc.identity.features.role.delete;

import com.primetech.poc.identity.features.role.infrastructure.RoleEntity;
import com.primetech.poc.identity.features.role.infrastructure.RoleRepository;
import com.primetech.poc.identity.shared.exception.BusinessException;
import com.primetech.poc.identity.shared.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delete Role Handler
 */
@Component
public class DeleteRoleHandler {

  private static final Logger log = LoggerFactory.getLogger(DeleteRoleHandler.class);

  private final RoleRepository repository;

  public DeleteRoleHandler(RoleRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void handle(DeleteRoleCommand command) {
    log.debug("Deleting role: {}", command.id());

    RoleEntity entity = repository.findById(command.id())
        .orElseThrow(() -> new EntityNotFoundException("Role", command.id()));

    if (entity.isSystem()) {
      throw new BusinessException("CANNOT_DELETE_SYSTEM_ROLE", "System roles cannot be deleted");
    }

    repository.deleteById(command.id());

    log.info("Role deleted: {}", command.id());
  }
}
