package com.primetech.poc.identity.features.user.infrastructure;

import com.primetech.poc.identity.features.user.create.CreateUserCommand;
import com.primetech.poc.identity.features.user.create.CreateUserRequest;
import com.primetech.poc.identity.features.user.create.UserResponse;
import com.primetech.poc.identity.features.user.domain.Email;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.domain.UserStatus;
import com.primetech.poc.identity.features.user.domain.Username;
import com.primetech.poc.identity.features.user.update.UpdateUserCommand;
import com.primetech.poc.identity.features.user.update.UpdateUserRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * User Mapper
 * <p>
 * MapStruct mapper for converting between User DTOs and domain objects.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    imports = {UUID.class, Instant.class, UserStatus.class, Email.class, Username.class},
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserMapper {

  // ========== Create User Mappings ==========

  /**
   * Map CreateUserRequest to CreateUserCommand.
   */
  CreateUserCommand toCommand(CreateUserRequest request);

  /**
   * Map CreateUserCommand to User domain model.
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "username", expression = "java(new Username(command.username()))")
  @Mapping(target = "email", expression = "java(new Email(command.email()))")
  @Mapping(target = "passwordHash", source = "passwordHash")
  @Mapping(target = "status", constant = "PENDING")
  @Mapping(target = "createdAt", source = "now")
  @Mapping(target = "updatedAt", source = "now")
  @Mapping(target = "deletedAt", expression = "java(null)")
  User toDomain(CreateUserCommand command, UUID id, String passwordHash, Instant now);

  // ========== Update User Mappings ==========

  /**
   * Map UpdateUserRequest to UpdateUserCommand.
   */
  @Mapping(target = "id", source = "id")
  UpdateUserCommand toCommand(UpdateUserRequest request, UUID id);

  /**
   * Merge non-null values from the update command into the existing user.
   */
  @Mapping(target = "id", source = "existing.id")
  @Mapping(target = "username", source = "existing.username")
  @Mapping(target = "email", source = "existing.email")
  @Mapping(target = "passwordHash", source = "existing.passwordHash")
  @Mapping(target = "status", source = "command.status")
  @Mapping(target = "firstName", source = "command.firstName")
  @Mapping(target = "lastName", source = "command.lastName")
  @Mapping(target = "phone", source = "command.phone")
  @Mapping(target = "preferredLanguage", source = "command.preferredLanguage")
  @Mapping(target = "timezone", source = "command.timezone")
  @Mapping(target = "createdAt", source = "existing.createdAt")
  @Mapping(target = "updatedAt", source = "now")
  @Mapping(target = "deletedAt", source = "existing.deletedAt")
  User mergeForUpdate(UpdateUserCommand command, User existing, Instant now);

  // ========== Response Mappings ==========

  /**
   * Map User domain model to UserResponse.
   */
  @Mapping(target = "username", expression = "java(user.username() != null ? user.username().value() : null)")
  @Mapping(target = "email", expression = "java(user.email() != null ? user.email().value() : null)")
  UserResponse toResponse(User user);

  /**
   * Map a list of User domain models to UserResponse list.
   */
  @Mapping(target = "username", expression = "java(user.username() != null ? user.username().value() : null)")
  @Mapping(target = "email", expression = "java(user.email() != null ? user.email().value() : null)")
  List<UserResponse> toResponseList(List<User> users);
}
