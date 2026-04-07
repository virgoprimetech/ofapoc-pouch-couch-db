package com.primetech.poc.identity.shared.multitenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hibernate Multi-Tenant Connection Provider for schema-based multi-tenancy.
 * <p>
 * This provider sets the PostgreSQL search_path to the tenant's schema
 * for each connection, enabling complete data isolation at the schema level.
 * <p>
 * Compatible with Hibernate 7.x / Spring Boot 4.x
 */
@Component
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

  private static final String PUBLIC_SCHEMA = "public";
  @Serial
  private static final long serialVersionUID = 1L;

  private final DataSource dataSource;

  @Override
  public Connection getAnyConnection() throws SQLException {
    return dataSource.getConnection();
  }

  @Override
  public void releaseAnyConnection(Connection connection) throws SQLException {
    connection.close();
  }

  @Override
  public Connection getConnection(String tenantIdentifier) throws SQLException {
    Connection connection = dataSource.getConnection();

    try {
      String schemaName = tenantIdentifier != null && !tenantIdentifier.isBlank()
          ? tenantIdentifier
          : PUBLIC_SCHEMA;

      // Sanitize schema name to prevent SQL injection
      if (!isValidSchemaName(schemaName)) {
        log.warn("Invalid schema name detected: {}, falling back to public", schemaName);
        schemaName = PUBLIC_SCHEMA;
      }

      // Set search_path to tenant schema + public (for shared tables like tenants registry)
      // public schema is needed for tenant lookup during authentication
      String searchPath = String.format("SET search_path TO %s", schemaName);
      log.debug("Setting search_path: {}", searchPath);

      connection.createStatement().execute(searchPath);
    } catch (SQLException e) {
      log.error("Failed to set tenant schema: {}", tenantIdentifier, e);
      try {
        connection.close();
      } catch (SQLException closeEx) {
        log.debug("Error closing connection after schema set failure", closeEx);
      }
      throw e;
    }

    return connection;
  }

  @Override
  public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
    try (connection) {
      // Reset to default schema before returning to pool
      connection.createStatement().execute("SET search_path TO public");
    } catch (SQLException e) {
      log.warn("Failed to reset search_path on connection release", e);
    }
  }

  @Override
  public boolean supportsAggressiveRelease() {
    // Return false for schema-based multi-tenancy to ensure connection
    // is held for the duration of the session
    return false;
  }

  @Override
  public <T> T unwrap(Class<T> unwrapType) {
    if (unwrapType.isInstance(this)) {
      return unwrapType.cast(this);
    }
    if (unwrapType.isInstance(dataSource)) {
      return unwrapType.cast(dataSource);
    }
    throw new HibernateException("Cannot unwrap to type: " + unwrapType.getName());
  }

  @Override
  public boolean isUnwrappableAs(Class<?> unwrapType) {
    return unwrapType.isInstance(this) ||
        (unwrapType.isInstance(dataSource));
  }

  /**
   * Validates schema name to prevent SQL injection.
   * Only allows alphanumeric characters and underscores.
   */
  private boolean isValidSchemaName(String schemaName) {
    if (schemaName == null || schemaName.isBlank()) {
      return false;
    }
    // Allow only alphanumeric and underscore, must start with letter or underscore
    return schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
  }
}
