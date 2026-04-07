package com.primetech.poc.identity.features.permission.list;

import com.primetech.poc.identity.features.permission.domain.Permission;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * Permission Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PermissionMapper {

  PermissionResponse toResponse(Permission permission);

  List<PermissionResponse> toResponseList(List<Permission> permissions);
}
