package com.primetech.poc.identity.features.assignment.infrastructure;

import com.primetech.poc.identity.features.assignment.assign.AssignRoleCommand;
import com.primetech.poc.identity.features.assignment.assign.AssignRoleRequest;
import com.primetech.poc.identity.features.assignment.assign.AssignmentResponse;
import com.primetech.poc.identity.features.assignment.domain.UserRoleAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UserRoleAssignment Mapper
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    imports = {UUID.class, Instant.class},
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserRoleAssignmentMapper {

  AssignRoleCommand toCommand(AssignRoleRequest request);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "createdAt", source = "now")
  @Mapping(target = "updatedAt", source = "now")
  @Mapping(target = "deletedAt", expression = "java(null)")
  UserRoleAssignment toDomain(AssignRoleCommand command, UUID id, Instant now);

  AssignmentResponse toResponse(UserRoleAssignment assignment);

  List<AssignmentResponse> toResponseList(List<UserRoleAssignment> assignments);
}
