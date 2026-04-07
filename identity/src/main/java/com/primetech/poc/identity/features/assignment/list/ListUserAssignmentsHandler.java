package com.primetech.poc.identity.features.assignment.list;

import com.primetech.poc.identity.features.assignment.domain.UserRoleAssignment;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentEntity;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentJpaMapper;
import com.primetech.poc.identity.features.assignment.infrastructure.UserRoleAssignmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * List User Assignments Handler
 */
@Component
public class ListUserAssignmentsHandler {

  private final UserRoleAssignmentRepository repository;
  private final UserRoleAssignmentJpaMapper jpaMapper;

  public ListUserAssignmentsHandler(
      UserRoleAssignmentRepository repository,
      UserRoleAssignmentJpaMapper jpaMapper) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
  }

  @Transactional(readOnly = true)
  public List<UserRoleAssignment> handle(ListUserAssignmentsQuery query) {
    List<UserRoleAssignmentEntity> entities;

    if (query.validOnly()) {
      entities = repository.findValidByUserId(query.userId());
    } else {
      entities = repository.findByUserId(query.userId());
    }

    return jpaMapper.toDomainList(entities);
  }
}
