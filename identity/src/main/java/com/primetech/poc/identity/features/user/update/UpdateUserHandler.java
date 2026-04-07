package com.primetech.poc.identity.features.user.update;

import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.infrastructure.UserEntity;
import com.primetech.poc.identity.features.user.infrastructure.UserJpaMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserRepository;
import com.primetech.poc.identity.shared.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Update User Handler
 * <p>
 * Handles updating existing users.
 */
@Component
public class UpdateUserHandler {

  private static final Logger log = LoggerFactory.getLogger(UpdateUserHandler.class);

  private final UserRepository repository;
  private final UserJpaMapper jpaMapper;
  private final UserMapper userMapper;

  public UpdateUserHandler(
      UserRepository repository,
      UserJpaMapper jpaMapper,
      UserMapper userMapper) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
    this.userMapper = userMapper;
  }

  /**
   * Handle user update
   *
   * @param command The update command
   * @return The updated user
   */
  @Transactional
  public User handle(UpdateUserCommand command) {
    log.debug("Updating user: {}", command.id());

    // Find existing user
    UserEntity entity = repository.findById(command.id())
        .orElseThrow(() -> new EntityNotFoundException("User", command.id()));

    User existing = jpaMapper.toDomain(entity);

    // Merge updates
    Instant now = Instant.now();
    User updated = userMapper.mergeForUpdate(command, existing, now);

    // Update entity and save
    jpaMapper.updateEntityFromDomain(updated, entity);
    entity.setUpdatedAt(now);

    UserEntity saved = repository.save(entity);

    log.info("User updated: {}", saved.getId());

    return jpaMapper.toDomain(saved);
  }
}
