package com.primetech.poc.identity.features.tenant.infrastructure;

import com.primetech.poc.identity.features.tenant.domain.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant JPA Entity.
 * <p>
 * Stored in the public schema as the tenant registry.
 * Each tenant has a corresponding PostgreSQL schema for their data.
 */
@Setter
@Getter
@Entity
@Table(name = "tenants", schema = "public")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@SQLRestriction("deleted_at IS NULL")
public class TenantEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "schema_name", nullable = false, unique = true, length = 100)
  private String schemaName;

  @Column(name = "display_name", nullable = false, length = 255)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TenantStatus status = TenantStatus.ACTIVE;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at", insertable = false, updatable = false)
  private Instant deletedAt;

  /**
   * Convert a tenant code to a schema name.
   */
  public static String codeToSchemaName(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Tenant code cannot be null or blank");
    }
    String normalized = code.toLowerCase().replaceAll("[^a-z0-9]", "_");
    return "tenant_" + normalized;
  }

  /**
   * Validate tenant code format.
   */
  public static boolean isValidCode(String code) {
    return code != null && code.matches("^[a-z][a-z0-9_-]*$");
  }

  /**
   * Check if the tenant is active.
   */
  public boolean isActive() {
    return status == TenantStatus.ACTIVE && deletedAt == null;
  }
}
