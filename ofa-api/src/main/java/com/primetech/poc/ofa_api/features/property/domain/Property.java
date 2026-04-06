package com.primetech.poc.ofa_api.features.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "properties")
public class Property {

  @Id
  private UUID id;

  @Column(name = "couchdb_rev", length = 100)
  private String couchdbRev;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "property_type", nullable = false, length = 30)
  private PropertyType propertyType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PropertyStatus status;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "address_line1", length = 500)
  private String addressLine1;

  @Column(name = "address_line2", length = 500)
  private String addressLine2;

  @Column(length = 255)
  private String city;

  @Column(name = "state_province", length = 255)
  private String stateProvince;

  @Column(name = "postal_code", length = 20)
  private String postalCode;

  @Column(length = 2)
  private String country;

  @Column(precision = 10, scale = 8)
  private BigDecimal latitude;

  @Column(precision = 11, scale = 8)
  private BigDecimal longitude;

  @Column(length = 50)
  private String phone;

  @Column(length = 255)
  private String email;

  @Column(length = 500)
  private String website;

  @Column(length = 50)
  private String timezone;

  @Column(length = 3)
  private String currency;

  @Column(name = "total_rooms")
  private Integer totalRooms;

  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  private Long version;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = updatedAt = Instant.now();
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  // --- Equals/HashCode on business key (ID) ---

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Property other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
