package com.primetech.poc.identity.features.assignment.revoke;

import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentRepository;
import com.primetech.poc.identity.shared.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revoke Assignment Handler
 */
@Component
public class RevokeAssignmentHandler {

  private static final Logger log = LoggerFactory.getLogger(RevokeAssignmentHandler.class);

  private final UserRoleAssignmentRepository repository;

  public RevokeAssignmentHandler(UserRoleAssignmentRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void handle(RevokeAssignmentCommand command) {
    log.debug("Revoking assignment: {}", command.id());

    if (!repository.existsById(command.id())) {
      throw new EntityNotFoundException("Assignment", command.id());
    }

    repository.deleteById(command.id());

    log.info("Assignment revoked: {}", command.id());
  }
}
