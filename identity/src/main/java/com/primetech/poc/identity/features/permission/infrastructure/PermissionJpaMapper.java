package com.primetech.poc.identity.features.permission.infrastructure;

import com.primetech.poc.identity.features.permission.domain.Permission;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * Permission JPA Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PermissionJpaMapper {

  Permission toDomain(PermissionEntity entity);

  PermissionEntity toEntity(Permission domain);

  List<Permission> toDomainList(List<PermissionEntity> entities);
}
