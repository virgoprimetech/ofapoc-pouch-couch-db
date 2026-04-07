package com.primetech.poc.identity.features.assignment.infrastructure;

import com.primetech.poc.identity.features.assignment.domain.UserRoleAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * UserRoleAssignment JPA Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserRoleAssignmentJpaMapper {

  UserRoleAssignment toDomain(UserRoleAssignmentEntity entity);

  UserRoleAssignmentEntity toEntity(UserRoleAssignment domain);

  List<UserRoleAssignment> toDomainList(List<UserRoleAssignmentEntity> entities);
}
