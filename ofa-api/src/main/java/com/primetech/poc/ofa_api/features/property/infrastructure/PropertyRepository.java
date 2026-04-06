package com.primetech.poc.ofa_api.features.property.infrastructure;

import com.primetech.poc.ofa_api.features.property.domain.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {
}
