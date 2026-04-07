package com.primetech.poc.identity.features.user.get;

import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.infrastructure.UserEntity;
import com.primetech.poc.identity.features.user.infrastructure.UserJpaMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserRepository;
import com.primetech.poc.identity.shared.exception.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Get User Handler
 * <p>
 * Handles retrieving a single user.
 */
@Component
public class GetUserHandler {

  private final UserRepository repository;
  private final UserJpaMapper jpaMapper;

  public GetUserHandler(UserRepository repository, UserJpaMapper jpaMapper) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
  }

  /**
   * Handle get user by ID
   *
   * @param query The get query
   * @return The user
   */
  @Transactional(readOnly = true)
  public User handle(GetUserQuery query) {
    UserEntity entity = repository.findById(query.id())
        .orElseThrow(() -> new EntityNotFoundException("User", query.id()));
    return jpaMapper.toDomain(entity);
  }
}
