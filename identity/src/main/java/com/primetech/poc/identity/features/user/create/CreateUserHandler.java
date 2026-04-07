package com.primetech.poc.identity.features.user.create;

import com.github.f4b6a3.uuid.UuidCreator;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.infrastructure.UserEntity;
import com.primetech.poc.identity.features.user.infrastructure.UserJpaMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserRepository;
import com.primetech.poc.identity.shared.exception.DuplicateEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Create User Handler
 * <p>
 * Handles the creation of new users.
 */
@Component
public class CreateUserHandler {

  private static final Logger log = LoggerFactory.getLogger(CreateUserHandler.class);

  private final UserRepository repository;
  private final UserJpaMapper jpaMapper;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  public CreateUserHandler(
      UserRepository repository,
      UserJpaMapper jpaMapper,
      UserMapper userMapper,
      PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Handle user creation
   *
   * @param command The create command
   * @return The created user
   */
  @Transactional
  public User handle(CreateUserCommand command) {
    log.debug("Creating user with username: {}", command.username());

    // Check if username already exists
    if (repository.existsByUsername(command.username())) {
      throw new DuplicateEntityException("User", "username", command.username());
    }

    // Check if email already exists
    if (repository.existsByEmail(command.email())) {
      throw new DuplicateEntityException("User", "email", command.email());
    }

    Instant now = Instant.now();
    UUID id = UuidCreator.getTimeOrderedEpoch();
    String passwordHash = passwordEncoder.encode(command.password());

    // Map command to domain model
    User user = userMapper.toDomain(command, id, passwordHash, now);

    // Convert to entity and save
    UserEntity entity = jpaMapper.toEntity(user);
    UserEntity saved = repository.save(entity);

    log.info("User created: {}", saved.getId());

    // Return domain model
    return jpaMapper.toDomain(saved);
  }
}
