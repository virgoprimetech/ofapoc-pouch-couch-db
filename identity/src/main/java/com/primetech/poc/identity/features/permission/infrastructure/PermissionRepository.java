package com.primetech.poc.identity.features.permission.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Permission Repository
 */
@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, UUID>, JpaSpecificationExecutor<PermissionEntity> {

  /**
   * Find permission by code
   */
  @Query("SELECT p FROM PermissionEntity p WHERE p.code = :code")
  Optional<PermissionEntity> findByCode(String code);

  /**
   * Find all permissions by resource
   */
  @Query("SELECT p FROM PermissionEntity p WHERE p.resource = :resource ORDER BY p.action")
  List<PermissionEntity> findByResource(String resource);

  /**
   * Find all permissions ordered by resource and action
   */
  @Query("SELECT p FROM PermissionEntity p ORDER BY p.resource, p.action")
  List<PermissionEntity> findAllOrderByResourceAndAction();

  /**
   * Find all distinct resources
   */
  @Query("SELECT DISTINCT p.resource FROM PermissionEntity p ORDER BY p.resource")
  List<String> findAllResources();
}
