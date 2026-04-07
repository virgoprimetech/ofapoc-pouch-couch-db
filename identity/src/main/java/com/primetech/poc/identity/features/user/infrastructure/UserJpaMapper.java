package com.primetech.poc.identity.features.user.infrastructure;

import com.primetech.poc.identity.features.user.domain.Email;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.domain.Username;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * User JPA Mapper
 * <p>
 * MapStruct mapper for converting between User domain model and UserEntity.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserJpaMapper {

  /**
   * Convert Entity to Domain.
   */
  @Mapping(target = "username", expression = "java(toUsername(entity.getUsername()))")
  @Mapping(target = "email", expression = "java(toEmail(entity.getEmail()))")
  User toDomain(UserEntity entity);

  /**
   * Convert Domain to Entity.
   */
  @Mapping(target = "username", expression = "java(domain.username() != null ? domain.username().value() : null)")
  @Mapping(target = "email", expression = "java(domain.email() != null ? domain.email().value() : null)")
  UserEntity toEntity(User domain);

  /**
   * Update Entity from Domain (for updates).
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  @Mapping(target = "username", expression = "java(domain.username() != null ? domain.username().value() : null)")
  @Mapping(target = "email", expression = "java(domain.email() != null ? domain.email().value() : null)")
  void updateEntityFromDomain(User domain, @MappingTarget UserEntity entity);

  /**
   * Convert a list of entities to domain models.
   */
  List<User> toDomainList(List<UserEntity> entities);

  /**
   * Helper method to create Username value object
   */
  default Username toUsername(String username) {
    if (username == null) {
      return null;
    }
    try {
      return new Username(username);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Helper method to create Email value object
   */
  default Email toEmail(String email) {
    if (email == null) {
      return null;
    }
    try {
      return new Email(email);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
