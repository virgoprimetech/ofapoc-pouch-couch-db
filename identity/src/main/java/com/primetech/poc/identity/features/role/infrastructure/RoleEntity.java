package com.primetech.poc.identity.features.role.infrastructure;

import com.primetech.poc.identity.features.role.domain.RoleScope;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Role JPA Entity
 * <p>
 * Database entity for Role persistence.
 */
@Entity
@Table(name = "roles")
@SQLDelete(sql = "UPDATE roles SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class RoleEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false, length = 20)
  private RoleScope scope;

  @Column(name = "is_system", nullable = false)
  private boolean isSystem = false;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @ElementCollection
  @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission_id")
  private Set<UUID> permissionIds = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public RoleScope getScope() {
    return scope;
  }

  public void setScope(RoleScope scope) {
    this.scope = scope;
  }

  public boolean isSystem() {
    return isSystem;
  }

  public void setSystem(boolean system) {
    isSystem = system;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Set<UUID> getPermissionIds() {
    return permissionIds;
  }

  public void setPermissionIds(Set<UUID> permissionIds) {
    this.permissionIds = permissionIds;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}
