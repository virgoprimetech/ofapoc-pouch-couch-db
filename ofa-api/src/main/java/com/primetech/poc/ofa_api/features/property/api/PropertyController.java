package com.primetech.poc.ofa_api.features.property.api;

import com.primetech.poc.ofa_api.features.property.infrastructure.PropertyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

  private final PropertyRepository propertyRepository;

  public PropertyController(PropertyRepository propertyRepository) {
    this.propertyRepository = propertyRepository;
  }

  @GetMapping
  public List<PropertyResponse> getAll() {
    return propertyRepository.findAll().stream()
        .map(PropertyResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  public ResponseEntity<PropertyResponse> getById(@PathVariable UUID id) {
    return propertyRepository.findById(id)
        .map(PropertyResponse::from)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
