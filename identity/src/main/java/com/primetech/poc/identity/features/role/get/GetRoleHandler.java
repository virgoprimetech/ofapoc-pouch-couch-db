package com.primetech.poc.identity.features.role.get;

import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleEntity;
import com.primetech.poc.identity.features.role.infrastructure.RoleJpaMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleRepository;
import com.primetech.poc.identity.shared.exception.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Get Role Handler
 */
@Component
public class GetRoleHandler {

  private final RoleRepository repository;
  private final RoleJpaMapper jpaMapper;

  public GetRoleHandler(RoleRepository repository, RoleJpaMapper jpaMapper) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
  }

  @Transactional(readOnly = true)
  public Role handle(GetRoleQuery query) {
    RoleEntity entity = repository.findById(query.id())
        .orElseThrow(() -> new EntityNotFoundException("Role", query.id()));
    return jpaMapper.toDomain(entity);
  }
}
