package com.primetech.poc.identity.features.role.infrastructure;

import com.primetech.poc.identity.features.role.domain.RoleScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Role Repository
 * <p>
 * Data access layer for Role entities.
 */
@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, UUID>, JpaSpecificationExecutor<RoleEntity> {

  /**
   * Find role by code
   */
  @Query("SELECT r FROM RoleEntity r WHERE r.code = :code")
  Optional<RoleEntity> findByCode(String code);

  /**
   * Check if role exists by code
   */
  @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RoleEntity r WHERE r.code = :code")
  boolean existsByCode(String code);

  /**
   * Find all roles by scope
   */
  @Query("SELECT r FROM RoleEntity r WHERE r.scope = :scope ORDER BY r.displayName")
  List<RoleEntity> findByScope(RoleScope scope);

  /**
   * Find all system roles
   */
  @Query("SELECT r FROM RoleEntity r WHERE r.isSystem = true ORDER BY r.displayName")
  List<RoleEntity> findByIsSystemTrue();

  /**
   * Find all roles ordered by display name
   */
  @Query("SELECT r FROM RoleEntity r ORDER BY r.scope, r.displayName")
  List<RoleEntity> findAllOrderByScopeAndDisplayName();
}
