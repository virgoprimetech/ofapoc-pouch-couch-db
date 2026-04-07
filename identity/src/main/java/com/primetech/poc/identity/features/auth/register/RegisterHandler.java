package com.primetech.poc.identity.features.auth.register;

import com.github.f4b6a3.uuid.UuidCreator;
import com.primetech.poc.identity.features.user.domain.Email;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.domain.UserStatus;
import com.primetech.poc.identity.features.user.domain.Username;
import com.primetech.poc.identity.features.user.infrastructure.UserEntity;
import com.primetech.poc.identity.features.user.infrastructure.UserJpaMapper;
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
 * Register Handler
 */
@Component
public class RegisterHandler {

  private static final Logger log = LoggerFactory.getLogger(RegisterHandler.class);

  private final UserRepository userRepository;
  private final UserJpaMapper userJpaMapper;
  private final PasswordEncoder passwordEncoder;

  public RegisterHandler(
      UserRepository userRepository,
      UserJpaMapper userJpaMapper,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.userJpaMapper = userJpaMapper;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public RegisterResponse handle(RegisterCommand command) {
    log.debug("Registering user with username: {}", command.username());

    // Check if username already exists
    if (userRepository.existsByUsername(command.username())) {
      throw new DuplicateEntityException("User", "username", command.username());
    }

    // Check if email already exists
    if (userRepository.existsByEmail(command.email())) {
      throw new DuplicateEntityException("User", "email", command.email());
    }

    Instant now = Instant.now();
    UUID id = UuidCreator.getTimeOrderedEpoch();
    String passwordHash = passwordEncoder.encode(command.password());

    // Create user with PENDING status (needs activation)
    User user = new User(
        id,
        new Username(command.username()),
        new Email(command.email()),
        passwordHash,
        UserStatus.ACTIVE,  // For POC, auto-activate. In production, this should be PENDING
        command.firstName(),
        command.lastName(),
        null,
        "en",
        "UTC",
        now,
        now,
        null
    );

    UserEntity entity = userJpaMapper.toEntity(user);
    UserEntity saved = userRepository.save(entity);

    log.info("User registered: {}", saved.getId());

    return new RegisterResponse(
        saved.getId(),
        saved.getUsername(),
        saved.getEmail(),
        saved.getStatus(),
        saved.getFirstName(),
        saved.getLastName(),
        saved.getCreatedAt()
    );
  }
}
