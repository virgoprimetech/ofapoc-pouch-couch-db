package com.primetech.poc.identity.features.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User Repository
 * <p>
 * Data access layer for User entities.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

  /**
   * Find user by username (primary login identifier)
   */
  @Query("SELECT u FROM UserEntity u WHERE u.username = :username")
  Optional<UserEntity> findByUsername(String username);

  /**
   * Check if user exists by username
   */
  @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE u.username = :username")
  boolean existsByUsername(String username);

  /**
   * Find user by email (kept for backward compatibility and notifications)
   */
  @Query("SELECT u FROM UserEntity u WHERE u.email = :email")
  Optional<UserEntity> findByEmail(String email);

  /**
   * Check if user exists by email
   */
  @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE u.email = :email")
  boolean existsByEmail(String email);

  /**
   * Find all users by status
   */
  @Query("SELECT u FROM UserEntity u WHERE u.status = :status ORDER BY u.createdAt DESC")
  List<UserEntity> findByStatus(com.primetech.poc.identity.features.user.domain.UserStatus status);

  /**
   * Find all users ordered by creation date
   */
  @Query("SELECT u FROM UserEntity u ORDER BY u.createdAt DESC")
  List<UserEntity> findAllOrderByCreatedAtDesc();
}
