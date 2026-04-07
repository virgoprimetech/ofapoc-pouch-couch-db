package com.primetech.poc.identity.features.user.delete;

import com.primetech.poc.identity.features.user.infrastructure.UserRepository;
import com.primetech.poc.identity.shared.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delete User Handler
 * <p>
 * Handles soft-deleting users.
 */
@Component
public class DeleteUserHandler {

  private static final Logger log = LoggerFactory.getLogger(DeleteUserHandler.class);

  private final UserRepository repository;

  public DeleteUserHandler(UserRepository repository) {
    this.repository = repository;
  }

  /**
   * Handle user deletion (soft delete)
   *
   * @param command The delete command
   */
  @Transactional
  public void handle(DeleteUserCommand command) {
    log.debug("Deleting user: {}", command.id());

    if (!repository.existsById(command.id())) {
      throw new EntityNotFoundException("User", command.id());
    }

    repository.deleteById(command.id());

    log.info("User deleted: {}", command.id());
  }
}
