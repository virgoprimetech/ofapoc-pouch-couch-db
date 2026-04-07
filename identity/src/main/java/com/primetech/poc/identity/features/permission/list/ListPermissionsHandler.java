package com.primetech.poc.identity.features.permission.list;

import com.primetech.poc.identity.features.permission.domain.Permission;
import com.primetech.poc.identity.features.permission.infrastructure.PermissionEntity;
import com.primetech.poc.identity.features.permission.infrastructure.PermissionJpaMapper;
import com.primetech.poc.identity.features.permission.infrastructure.PermissionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * List Permissions Handler
 */
@Component
public class ListPermissionsHandler {

  private final PermissionRepository repository;
  private final PermissionJpaMapper jpaMapper;

  public ListPermissionsHandler(PermissionRepository repository, PermissionJpaMapper jpaMapper) {
    this.repository = repository;
    this.jpaMapper = jpaMapper;
  }

  @Transactional(readOnly = true)
  public List<Permission> handle(ListPermissionsQuery query) {
    List<PermissionEntity> entities;

    if (query.resource() != null) {
      entities = repository.findByResource(query.resource());
    } else {
      entities = repository.findAllOrderByResourceAndAction();
    }

    return jpaMapper.toDomainList(entities);
  }
}
