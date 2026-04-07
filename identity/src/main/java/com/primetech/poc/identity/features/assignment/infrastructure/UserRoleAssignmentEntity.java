package com.primetech.poc.identity.features.assignment.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * UserRoleAssignment JPA Entity
 */
@Setter
@Getter
@Entity
@Table(name = "user_role_assignments")
@SQLDelete(sql = "UPDATE user_role_assignments SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class UserRoleAssignmentEntity {

  // Getters and Setters
  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Column(name = "property_id")
  private UUID propertyId;

  @Column(name = "chain_id")
  private UUID chainId;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_until")
  private Instant validUntil;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

}
