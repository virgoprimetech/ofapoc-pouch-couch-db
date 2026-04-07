package com.primetech.poc.identity.features.assignment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UserRoleAssignment Repository
 */
@Repository
public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignmentEntity, UUID>, JpaSpecificationExecutor<UserRoleAssignmentEntity> {

  /**
   * Find all assignments for a user
   */
  @Query("SELECT a FROM UserRoleAssignmentEntity a WHERE a.userId = :userId ORDER BY a.createdAt DESC")
  List<UserRoleAssignmentEntity> findByUserId(UUID userId);

  /**
   * Find all assignments for a user that are currently valid
   */
  @Query("SELECT a FROM UserRoleAssignmentEntity a WHERE a.userId = :userId " +
      "AND a.validFrom <= CURRENT_TIMESTAMP " +
      "AND (a.validUntil IS NULL OR a.validUntil > CURRENT_TIMESTAMP) " +
      "ORDER BY a.createdAt DESC")
  List<UserRoleAssignmentEntity> findValidByUserId(UUID userId);

  /**
   * Find assignment by user, role, and optional property
   */
  @Query("SELECT a FROM UserRoleAssignmentEntity a WHERE a.userId = :userId AND a.roleId = :roleId " +
      "AND ((:propertyId IS NULL AND a.propertyId IS NULL) OR a.propertyId = :propertyId)")
  Optional<UserRoleAssignmentEntity> findByUserIdAndRoleIdAndPropertyId(UUID userId, UUID roleId, UUID propertyId);

  /**
   * Find all assignments for a property
   */
  @Query("SELECT a FROM UserRoleAssignmentEntity a WHERE a.propertyId = :propertyId ORDER BY a.createdAt DESC")
  List<UserRoleAssignmentEntity> findByPropertyId(UUID propertyId);

  /**
   * Find all assignments for a chain
   */
  @Query("SELECT a FROM UserRoleAssignmentEntity a WHERE a.chainId = :chainId ORDER BY a.createdAt DESC")
  List<UserRoleAssignmentEntity> findByChainId(UUID chainId);
}
