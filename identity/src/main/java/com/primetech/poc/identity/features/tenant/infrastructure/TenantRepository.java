package com.primetech.poc.identity.features.tenant.infrastructure;

import com.primetech.poc.identity.features.tenant.domain.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant Repository.
 * <p>
 * Data access layer for Tenant entities.
 * Always operates on the public schema.
 */
@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

  /**
   * Find tenant by code (used during login)
   */
  @Query(value = "SELECT t FROM TenantEntity t WHERE t.code = :code")
  Optional<TenantEntity> findByCode(String code);

  /**
   * Find tenant by schema name
   */
  @Query(value = "SELECT t FROM TenantEntity t WHERE t.schemaName = :schemaName")
  Optional<TenantEntity> findBySchemaName(String schemaName);

  /**
   * Check if tenant exists by code
   */
  @Query(value = "SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TenantEntity t WHERE t.code = :code")
  boolean existsByCode(String code);

  /**
   * Find all tenants by status
   */
  @Query(value = "SELECT t FROM TenantEntity t WHERE t.status = :status ORDER BY t.createdAt DESC")
  List<TenantEntity> findByStatus(TenantStatus status);

  /**
   * Find all tenants ordered by creation date
   */
  @Query(value = "SELECT t FROM TenantEntity t ORDER BY t.createdAt DESC")
  List<TenantEntity> findAllOrderByCreatedAtDesc();
}
