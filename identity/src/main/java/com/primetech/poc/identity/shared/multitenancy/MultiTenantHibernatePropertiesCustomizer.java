package com.primetech.poc.identity.shared.multitenancy;

import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Customizes Hibernate properties to enable schema-based multi-tenancy.
 * <p>
 * This approach preserves Spring Boot's auto-configuration while injecting
 * the multi-tenancy strategy beans. Prefer this over creating a custom
 * EntityManagerFactory bean.
 * <p>
 * Uses {@link MultiTenancySettings} constants for type-safe property keys.
 * <p>
 * Compatible with Hibernate 7.x / Spring Boot 4.x
 */
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class MultiTenantHibernatePropertiesCustomizer implements HibernatePropertiesCustomizer {

  private final MultiTenantConnectionProvider<String> connectionProvider;
  private final CurrentTenantIdentifierResolver<String> tenantResolver;

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    // Inject multi-tenancy strategy instances using MultiTenancySettings constants
    hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
    hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
  }
}
