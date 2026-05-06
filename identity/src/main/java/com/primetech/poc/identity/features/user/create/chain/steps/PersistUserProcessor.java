package com.primetech.poc.identity.features.user.create.chain.steps;

import com.github.f4b6a3.uuid.UuidCreator;
import com.primetech.poc.identity.features.user.create.chain.CreateUserChain;
import com.primetech.poc.identity.features.user.create.chain.CreateUserContext;
import com.primetech.poc.identity.features.user.create.chain.CreateUserProcessor;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.infrastructure.UserEntity;
import com.primetech.poc.identity.features.user.infrastructure.UserJpaMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import static com.primetech.poc.identity.features.user.create.chain.CreateUserChainStep.PERSIST_USER;

import java.time.Instant;
import java.util.UUID;

@Component
public class PersistUserProcessor implements CreateUserProcessor {

  private static final Logger log = LoggerFactory.getLogger(PersistUserProcessor.class);

  private final UserRepository repository;
  private final UserJpaMapper jpaMapper;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  public PersistUserProcessor(
      UserRepository repository,
      UserJpaMapper jpaMapper,
      UserMapper userMapper,
      PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void process(CreateUserContext context, CreateUserChain chain) {
    Instant now = Instant.now();
    UUID id = UuidCreator.getTimeOrderedEpoch();
    String passwordHash = passwordEncoder.encode(context.getCommand().password());

    User user = userMapper.toDomain(context.getCommand(), id, passwordHash, now);
    UserEntity entity = jpaMapper.toEntity(user);
    UserEntity saved = repository.save(entity);

    log.info("User created: {}", saved.getId());

    context.setUser(jpaMapper.toDomain(saved));
    chain.process(context);
  }

  @Override
  public int getOrder() {
    return PERSIST_USER.order();
  }
}
