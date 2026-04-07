package com.primetech.poc.identity.features.role.infrastructure;

import com.primetech.poc.identity.features.role.domain.Role;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Role JPA Mapper
 * <p>
 * MapStruct mapper for converting between Role domain model and RoleEntity.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RoleJpaMapper {

  /**
   * Convert Entity to Domain.
   */
  Role toDomain(RoleEntity entity);

  /**
   * Convert Domain to Entity.
   */
  RoleEntity toEntity(Role domain);

  /**
   * Update Entity from Domain (for updates).
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  void updateEntityFromDomain(Role domain, @MappingTarget RoleEntity entity);

  /**
   * Convert a list of entities to domain models.
   */
  List<Role> toDomainList(List<RoleEntity> entities);
}
