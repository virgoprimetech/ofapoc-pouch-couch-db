package com.primetech.poc.identity.features.role.list;

import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.infrastructure.RoleEntity;
import com.primetech.poc.identity.features.role.infrastructure.RoleJpaMapper;
import com.primetech.poc.identity.features.role.infrastructure.RoleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * List Roles Handler
 */
@Component
public class ListRolesHandler {

  private final RoleRepository repository;
  private final RoleJpaMapper jpaMapper;

  public ListRolesHandler(RoleRepository repository, RoleJpaMapper jpaMapper) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
  }

  @Transactional(readOnly = true)
  public List<Role> handle(ListRolesQuery query) {
    List<RoleEntity> entities;

    if (query.scope() != null) {
      entities = repository.findByScope(query.scope());
    } else {
      entities = repository.findAllOrderByScopeAndDisplayName();
    }

    return jpaMapper.toDomainList(entities);
  }
}
