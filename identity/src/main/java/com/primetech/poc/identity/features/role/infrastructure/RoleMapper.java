package com.primetech.poc.identity.features.role.infrastructure;

import com.primetech.poc.identity.features.role.create.CreateRoleCommand;
import com.primetech.poc.identity.features.role.create.CreateRoleRequest;
import com.primetech.poc.identity.features.role.create.RoleResponse;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.role.update.UpdateRoleCommand;
import com.primetech.poc.identity.features.role.update.UpdateRoleRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Role Mapper
 * <p>
 * MapStruct mapper for converting between Role DTOs and domain objects.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    imports = {UUID.class, Instant.class},
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface RoleMapper {

  // ========== Create Role Mappings ==========

  /**
   * Map CreateRoleRequest to CreateRoleCommand.
   */
  CreateRoleCommand toCommand(CreateRoleRequest request);

  /**
   * Map CreateRoleCommand to Role domain model.
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "isSystem", constant = "false")
  @Mapping(target = "createdAt", source = "now")
  @Mapping(target = "updatedAt", source = "now")
  @Mapping(target = "deletedAt", expression = "java(null)")
  Role toDomain(CreateRoleCommand command, UUID id, Instant now);

  // ========== Update Role Mappings ==========

  /**
   * Map UpdateRoleRequest to UpdateRoleCommand.
   */
  @Mapping(target = "id", source = "id")
  UpdateRoleCommand toCommand(UpdateRoleRequest request, UUID id);

  /**
   * Merge non-null values from the update command into the existing role.
   */
  @Mapping(target = "id", source = "existing.id")
  @Mapping(target = "code", source = "existing.code")
  @Mapping(target = "displayName", source = "command.displayName")
  @Mapping(target = "scope", source = "command.scope")
  @Mapping(target = "isSystem", source = "existing.isSystem")
  @Mapping(target = "description", source = "command.description")
  @Mapping(target = "permissionIds", source = "command.permissionIds")
  @Mapping(target = "createdAt", source = "existing.createdAt")
  @Mapping(target = "updatedAt", source = "now")
  @Mapping(target = "deletedAt", source = "existing.deletedAt")
  Role mergeForUpdate(UpdateRoleCommand command, Role existing, Instant now);

  // ========== Response Mappings ==========

  /**
   * Map Role domain model to RoleResponse.
   */
  RoleResponse toResponse(Role role);

  /**
   * Map a list of Role domain models to RoleResponse list.
   */
  List<RoleResponse> toResponseList(List<Role> roles);
}
