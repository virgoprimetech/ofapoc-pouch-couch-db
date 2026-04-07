package com.primetech.poc.identity.features.user.list;

import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.infrastructure.UserEntity;
import com.primetech.poc.identity.features.user.infrastructure.UserJpaMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * List Users Handler
 * <p>
 * Handles listing users.
 */
@Component
public class ListUsersHandler {

  private final UserRepository repository;
  private final UserJpaMapper jpaMapper;

  public ListUsersHandler(UserRepository repository, UserJpaMapper jpaMapper) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
  }

  /**
   * Handle list users
   *
   * @param query The list query
   * @return The list of users
   */
  @Transactional(readOnly = true)
  public List<User> handle(ListUsersQuery query) {
    List<UserEntity> entities;

    if (query.status() != null) {
      entities = repository.findByStatus(query.status());
    } else {
      entities = repository.findAllOrderByCreatedAtDesc();
    }

    return jpaMapper.toDomainList(entities);
  }
}
