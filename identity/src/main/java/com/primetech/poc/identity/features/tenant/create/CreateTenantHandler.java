package com.primetech.poc.identity.features.tenant.create;

import com.primetech.poc.identity.features.tenant.domain.TenantStatus;
import com.primetech.poc.identity.features.tenant.infrastructure.TenantEntity;
import com.primetech.poc.identity.features.tenant.infrastructure.TenantRepository;
import com.primetech.poc.identity.shared.exception.BusinessException;
import com.primetech.poc.identity.shared.multitenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * Create Tenant Handler - Pragmatic Architecture
 * <p>
 * Uses JPA entities directly without separate domain layer.
 */
@Component
public class CreateTenantHandler {

  private static final Logger log = LoggerFactory.getLogger(CreateTenantHandler.class);
  private static final String TENANT_ADMIN_ROLE_ID = "10000000-0000-0000-0000-000000000002";

  private final TenantRepository tenantRepository;
  private final PasswordEncoder passwordEncoder;
  private final DataSource dataSource;

  public CreateTenantHandler(
      TenantRepository tenantRepository,
      PasswordEncoder passwordEncoder,
      DataSource dataSource) {
    this.tenantRepository = tenantRepository;
    this.passwordEncoder = passwordEncoder;
    this.dataSource = dataSource;
  }

  @Transactional
  public TenantResponse handle(CreateTenantCommand command) {
    log.info("Creating tenant: {}", command.code());

    // Validate tenant code format
    if (!TenantEntity.isValidCode(command.code())) {
      throw new BusinessException("Invalid tenant code format");
    }

    // Check if tenant code already exists
    if (tenantRepository.existsByCode(command.code())) {
      throw new BusinessException("Tenant code already exists: " + command.code());
    }

    // Generate schema name from code
    String schemaName = TenantEntity.codeToSchemaName(command.code());

    // Create tenant entity in public schema
    Instant now = Instant.now();
    TenantEntity tenantEntity = new TenantEntity();
    tenantEntity.setId(UUID.randomUUID()); // Generate UUID for ID
    tenantEntity.setCode(command.code());
    tenantEntity.setSchemaName(schemaName);
    tenantEntity.setDisplayName(command.displayName());
    tenantEntity.setStatus(TenantStatus.ACTIVE);
    tenantEntity.setCreatedAt(now);
    tenantEntity.setUpdatedAt(now);

    // Save tenant to public schema
    tenantEntity = tenantRepository.save(tenantEntity);

    try {
      // Set tenant context for schema operations
      TenantContext.setTenant(tenantEntity.getCode(), tenantEntity.getId(), schemaName);

      // Create tenant schema using PostgreSQL function
      createTenantSchema(schemaName);

      // Create admin user in tenant schema
      createUserInTenantSchema(command, schemaName);

      log.info("Tenant created successfully: {}", tenantEntity.getId());

      return new TenantResponse(
          tenantEntity.getId().toString(),
          tenantEntity.getCode(),
          tenantEntity.getSchemaName(),
          tenantEntity.getDisplayName(),
          tenantEntity.getStatus(),
          tenantEntity.getCreatedAt()
      );
    } finally {
      // Always clear tenant context
      TenantContext.clear();
    }
  }

  private void createTenantSchema(String schemaName) {
    try (Connection connection = dataSource.getConnection()) {
      // Call the PostgreSQL function to create schema
      String sql = String.format("SELECT public.create_tenant_schema('%s')", schemaName);
      connection.createStatement().execute(sql);
      log.debug("Created tenant schema: {}", schemaName);
    } catch (SQLException e) {
      log.error("Failed to create tenant schema: {}", schemaName, e);
      throw new BusinessException("Failed to create tenant schema: " + e.getMessage());
    }
  }

  private void createUserInTenantSchema(CreateTenantCommand command, String schemaName) {
    try (Connection connection = dataSource.getConnection()) {
      // Set search_path to tenant schema
      connection.createStatement().execute(String.format("SET search_path TO %s", schemaName));

      Instant now = Instant.now();

      // Insert admin user directly using SQL (to bypass Hibernate's schema handling)
      String insertUserSql = String.format(
          "INSERT INTO %s.users (id, username, email, password_hash, status, first_name, last_name, created_at, updated_at) " +
              "VALUES (uuidv7(), '%s', '%s', '%s', 'ACTIVE', 'Admin', 'User', NOW(), NOW())",
          schemaName,
          escapeSql(command.adminUsername()),
          escapeSql(command.adminEmail()),
          passwordEncoder.encode(command.adminPassword())
      );
      connection.createStatement().execute(insertUserSql);

      // Get the created user ID
      var rs = connection.createStatement().executeQuery(
          String.format("SELECT id FROM %s.users WHERE username = '%s'", schemaName, escapeSql(command.adminUsername()))
      );
      String userId = null;
      if (rs.next()) {
        userId = rs.getString("id");
      }

      if (userId != null) {
        // Assign TENANT_ADMIN role to the admin user
        String insertAssignmentSql = String.format(
            "INSERT INTO %s.user_role_assignments (id, user_id, role_id, valid_from, created_at, updated_at) " +
                "VALUES (uuidv7(), '%s', '%s', NOW(), NOW(), NOW())",
            schemaName, userId, TENANT_ADMIN_ROLE_ID
        );
        connection.createStatement().execute(insertAssignmentSql);
      }

      log.debug("Created admin user in tenant schema: {}", schemaName);
    } catch (SQLException e) {
      log.error("Failed to create admin user in tenant schema: {}", schemaName, e);
      throw new BusinessException("Failed to create admin user: " + e.getMessage());
    }
  }

  private String escapeSql(String value) {
    if (value == null) return null;
    return value.replace("'", "''");
  }
}
