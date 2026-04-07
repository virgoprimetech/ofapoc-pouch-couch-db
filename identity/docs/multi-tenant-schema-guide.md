# Multi-Tenant Schema-Based Implementation Guide

This guide documents the implementation of schema-based multi-tenancy in Spring Boot 4.x with Hibernate 7.x. The approach uses PostgreSQL schemas to isolate tenant data while sharing a single database.

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Database Setup](#2-database-setup)
3. [Hibernate Configuration](#3-hibernate-configuration)
4. [Spring Integration](#4-spring-integration)
5. [Request Flow](#5-request-flow)
6. [Code Examples](#6-code-examples)
7. [Best Practices](#7-best-practices)
8. [Security Considerations](#8-security-considerations)
9. [Testing Multi-Tenant Code](#9-testing-multi-tenant-code)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Architecture Overview

### What is Schema-Based Multi-Tenancy

Schema-based multi-tenancy uses separate PostgreSQL schemas within a single database to isolate tenant data. Each tenant gets their own schema (e.g., `tenant_acme`, `tenant_globex`), while sharing the same database instance and connection pool.

```
┌─────────────────────────────────────────────────────────────────┐
│                      PostgreSQL Database                         │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   public    │  │ tenant_acme │  │    tenant_globex        │  │
│  │   schema    │  │   schema    │  │       schema            │  │
│  │             │  │             │  │                         │  │
│  │ - tenants   │  │ - users     │  │ - users                 │  │
│  │   (registry)│  │ - roles     │  │ - roles                 │  │
│  │             │  │ - perms     │  │ - perms                 │  │
│  │             │  │ - props     │  │ - props                 │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Benefits vs Database-Per-Tenant Approach

| Aspect | Schema-Based (This Implementation) | Database-Per-Tenant |
|--------|-----------------------------------|---------------------|
| **Resource Usage** | Single database, shared connection pool | Separate database per tenant |
| **Cost** | Lower (fewer database instances) | Higher (more infrastructure) |
| **Isolation** | Schema-level (good for most cases) | Database-level (strongest isolation) |
| **Backup/Restore** | Per-schema or full database | Per-database (more granular) |
| **Scaling** | Vertical (add resources to DB) | Horizontal (distribute DBs) |
| **Complexity** | Moderate (schema switching) | Higher (multiple connections) |
| **Tenant Onboarding** | Fast (create schema) | Slower (provision new DB) |

Choose schema-based multi-tenancy when:
- You have many tenants with moderate data volumes
- Cost efficiency is a priority
- You need fast tenant provisioning
- Regulatory requirements allow schema-level isolation

### How Tenant Context Flows Through the Application

```
┌──────────────┐     ┌───────────────┐     ┌──────────────────┐     ┌─────────────┐
│   HTTP       │     │    JWT        │     │   TenantContext  │     │  Hibernate  │
│   Request    │────▶│   Filter      │────▶│   (ThreadLocal)  │────▶│   Session   │
│              │     │               │     │                  │     │             │
│ Header:      │     │ Extract:      │     │ Stores:          │     │ Uses:       │
│ Authorization│     │ - tenant_code │     │ - tenantCode     │     │ search_path │
│              │     │ - user_id     │     │ - schemaName     │     │ = tenant_X  │
│              │     │ - roles       │     │ - tenantId       │     │             │
└──────────────┘     └───────────────┘     └──────────────────┘     └─────────────┘
                                                      │
                                                      ▼
                                           ┌──────────────────┐
                                           │  Request End     │
                                           │  (Filter clears  │
                                           │   context)       │
                                           └──────────────────┘
```

**Flow Steps**:

1. **Request arrives** with JWT in Authorization header
2. **JWT filter** validates token and extracts claims
3. **TenantContext** is populated with tenant code and schema name
4. **Hibernate** resolves tenant via `CurrentTenantIdentifierResolver`
5. **Connection provider** sets `search_path` to tenant schema
6. **Repository** operations use the correct schema automatically
7. **Request completes** and filter clears `TenantContext`

---

## 2. Database Setup

### PostgreSQL Schema Structure

The database uses a registry pattern with the `public` schema storing tenant metadata and individual tenant schemas storing tenant-specific data.

**Public Schema (Registry)**:
```sql
-- Tenant registry in public schema
CREATE TABLE public.tenants (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    code            VARCHAR(50) NOT NULL UNIQUE,
    schema_name     VARCHAR(100) NOT NULL UNIQUE,
    display_name    VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE
);
```

**Tenant Schema (Per-Tenant)**:
```sql
-- Each tenant schema contains identical table structures
CREATE TABLE tenant_acme.users (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    username        VARCHAR(50) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- ... additional columns
);

CREATE TABLE tenant_acme.roles (...);
CREATE TABLE tenant_acme.permissions (...);
-- ... other tenant tables
```

### Default Public Schema vs Tenant Schemas

| Schema | Purpose | Contents |
|--------|---------|----------|
| `public` | System/Registry | `tenants` table, system-wide config |
| `tenant_{code}` | Tenant Data | `users`, `roles`, `properties`, etc. |

**Naming Convention**:
- Tenant code: `acme`, `globex`, `acme-hotels`
- Schema name: `tenant_acme`, `tenant_globex`, `tenant_acme_hotels`

### Liquibase Migrations for Schema Management

**Master Changelog** (`db/db.changelog-master.yaml`):

```yaml
databaseChangeLog:
  # Public schema migrations (tenant registry)
  - include:
      file: changelog/V008__create_tenants_table_public.sql
      relativeToChangelogFile: true
  - include:
      file: changelog/V009__create_tenant_schema_function.sql
      relativeToChangelogFile: true
```

**Tenant Registry Migration** (`V008__create_tenants_table_public.sql`):

```sql
-- liquibase formatted sql

-- changeset identity:008-create-tenants-table-public
CREATE TABLE IF NOT EXISTS public.tenants (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    code            VARCHAR(50) NOT NULL UNIQUE,
    schema_name     VARCHAR(100) NOT NULL UNIQUE,
    display_name    VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE
);

-- Indexes for tenant lookups
CREATE INDEX idx_tenants_code ON public.tenants(code) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_schema_name ON public.tenants(schema_name) WHERE deleted_at IS NULL;

-- Default tenant for system operations
INSERT INTO public.tenants (id, code, schema_name, display_name, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'public',
    'public',
    'Default Tenant',
    'ACTIVE'
);
```

**Schema Creation Function** (`V009__create_tenant_schema_function.sql`):

This PostgreSQL function creates a complete tenant schema with all tables:

```sql
-- liquibase formatted sql

-- changeset identity:009-create-tenant-schema-function splitStatements:false
CREATE OR REPLACE FUNCTION public.create_tenant_schema(p_schema_name VARCHAR)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- Validate schema name format
    IF p_schema_name NOT LIKE 'tenant_%' THEN
        RAISE EXCEPTION 'Schema name must start with "tenant_"';
    END IF;

    -- Create the schema
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', p_schema_name);

    -- Create users table in tenant schema
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.users (
            id              UUID PRIMARY KEY DEFAULT uuidv7(),
            username        VARCHAR(50) NOT NULL UNIQUE,
            email           VARCHAR(255) NOT NULL UNIQUE,
            password_hash   VARCHAR(255) NOT NULL,
            status          VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'',
            created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            deleted_at      TIMESTAMP WITH TIME ZONE
        )', p_schema_name);

    -- Create additional tables...
    -- (roles, permissions, etc.)

    -- Seed default roles and permissions
    -- ...
END;
$$;
```

---

## 3. Hibernate Configuration

### Hibernate Multi-Tenancy Settings

In Hibernate 7.x with Spring Boot 4.x, multi-tenancy is configured programmatically via `HibernatePropertiesCustomizer` using `MultiTenancySettings` constants:

```java
import org.hibernate.cfg.MultiTenancySettings;

// Use these constants instead of hardcoded strings:
MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER   // "hibernate.multi_tenant_connection_provider"
MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER   // "hibernate.tenant_identifier_resolver"
```

| Setting | Constant | Value | Purpose |
|---------|----------|-------|---------|
| `hibernate.multiTenancy` | - | `SCHEMA` | Enables schema-based multi-tenancy |
| `hibernate.multi_tenant_connection_provider` | `MULTI_TENANT_CONNECTION_PROVIDER` | Custom bean | Provides connections with schema set |
| `hibernate.tenant_identifier_resolver` | `MULTI_TENANT_IDENTIFIER_RESOLVER` | Custom bean | Resolves current tenant from context |

### MultiTenantConnectionProvider Implementation

This provider sets the PostgreSQL `search_path` for each connection:

```java
package com.primetech.poc.identity.shared.multitenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hibernate Multi-Tenant Connection Provider for schema-based multi-tenancy.
 *
 * Sets the PostgreSQL search_path to the tenant's schema for each connection,
 * enabling complete data isolation at the schema level.
 */
@Component
@RequiredArgsConstructor
public class SchemaMultiTenantConnectionProvider
        implements MultiTenantConnectionProvider<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PUBLIC_SCHEMA = "public";

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
        String schemaName = tenantIdentifier != null ? tenantIdentifier : PUBLIC_SCHEMA;

        // Validate schema name to prevent SQL injection
        if (!isValidSchemaName(schemaName)) {
            throw new SQLException("Invalid schema name: " + schemaName);
        }

        Connection connection = dataSource.getConnection();

        try {
            // Set search_path to tenant schema + public (for shared tables)
            String searchPath = String.format("SET search_path TO %s, public", schemaName);
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
    public void releaseConnection(String tenantIdentifier, Connection connection)
            throws SQLException {
        try {
            // Reset to default schema before returning to pool
            connection.createStatement().execute("SET search_path TO public");
        } catch (SQLException e) {
            log.warn("Failed to reset search_path on connection release", e);
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        // Return false to ensure connection is held for the session duration
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (unwrapType.isInstance(this)) {
            return unwrapType.cast(this);
        }
        if (dataSource != null && unwrapType.isInstance(dataSource)) {
            return unwrapType.cast(dataSource);
        }
        throw new HibernateException("Cannot unwrap to type: " + unwrapType.getName());
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType.isInstance(this) ||
               (dataSource != null && unwrapType.isInstance(dataSource));
    }

    /**
     * Validates schema name to prevent SQL injection.
     */
    private boolean isValidSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            return false;
        }
        // Allow only alphanumeric and underscore, must start with letter/underscore
        return schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
}
```

### CurrentTenantIdentifierResolver Implementation

This resolver provides the current tenant identifier to Hibernate:

```java
package com.primetech.poc.identity.shared.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hibernate Tenant Identifier Resolver.
 *
 * Resolves the current tenant identifier from the TenantContext thread-local storage.
 */
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String> {

    private static final Logger log = LoggerFactory.getLogger(TenantIdentifierResolver.class);
    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schemaName = TenantContext.getSchemaName();

        if (schemaName != null && !schemaName.isBlank()) {
            log.trace("Resolved tenant schema: {}", schemaName);
            return schemaName;
        }

        log.trace("No tenant context set, using default schema: {}", DEFAULT_TENANT);
        return DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Return false to allow new sessions with different tenant identifiers
        return false;
    }
}
```

---

## 4. Spring Integration

### DataSource Configuration with HikariCP

The `application.yaml` configures HikariCP for connection pooling:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/identity
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      idle-timeout: 30000
      connection-timeout: 20000
      max-lifetime: 1800000
      pool-name: HikariCP-Identity
      connection-test-query: SELECT 1
```

### Multi-Tenant JPA Configuration

In Spring Boot 4.x with Hibernate 7.x, use `HibernatePropertiesCustomizer` with `MultiTenancySettings` constants. This preserves Spring Boot's auto-configuration:

```java
package com.primetech.poc.identity.shared.multitenancy;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Customizes Hibernate properties to enable schema-based multi-tenancy.
 *
 * Uses {@link MultiTenancySettings} constants for type-safe property keys.
 * This approach preserves Spring Boot's auto-configuration.
 */
@Component
public class MultiTenantHibernatePropertiesCustomizer implements HibernatePropertiesCustomizer {

    private final MultiTenantConnectionProvider<String> connectionProvider;
    private final CurrentTenantIdentifierResolver<String> tenantResolver;

    public MultiTenantHibernatePropertiesCustomizer(
            MultiTenantConnectionProvider<String> connectionProvider,
            CurrentTenantIdentifierResolver<String> tenantResolver) {
        this.connectionProvider = connectionProvider;
        this.tenantResolver = tenantResolver;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        // Inject multi-tenancy strategy instances using MultiTenancySettings constants
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
    }
}
```

### Why Not application.yaml?

Schema-based multi-tenancy **cannot** be configured purely via `application.yaml` because:

| Component | Can Be YAML? | Reason |
|-----------|--------------|--------|
| `MULTI_TENANT_CONNECTION_PROVIDER` | **No** | Contains business logic (`SET search_path`) |
| `TENANT_IDENTIFIER_RESOLVER` | **No** | Reads from `TenantContext` thread-local |
| `format_sql`, `batch_size`, `ddl-auto` | **Yes** | Already in your yaml |

Hibernate's `StrategySelector` only accepts:
- An actual instance of the strategy
- A Class reference
- A fully qualified class name string

It does **not** support Spring bean name strings like `"schemaMultiTenantConnectionProvider"`.

### MultiTenantConfig.java (Simplified)

```java
package com.primetech.poc.identity.shared.multitenancy;

import org.springframework.context.annotation.Configuration;

/**
 * Multi-Tenancy Configuration.
 *
 * Schema-based multi-tenancy is configured via {@link MultiTenantHibernatePropertiesCustomizer}
 * which preserves Spring Boot's auto-configuration while injecting the multi-tenancy
 * strategy beans.
 *
 * Compatible with Hibernate 7.x / Spring Boot 4.x
 */
@Configuration
public class MultiTenantConfig {
    // No custom EntityManagerFactory bean needed.
    // Multi-tenancy is configured via MultiTenantHibernatePropertiesCustomizer.
    // Spring Boot's auto-configuration handles EMF and TransactionManager.
}
```

---

## 5. Request Flow

### Extract Tenant from JWT Token

The JWT authentication converter extracts tenant information and populates `TenantContext`:

```java
package com.primetech.poc.ofa_api.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Custom JWT authentication converter that extracts user and tenant information.
 */
public class JwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_TENANT_CODE = "tenant_code";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        UserPrincipal principal = extractPrincipal(jwt);

        // Set tenant context for this request
        TenantContext.setCurrentTenant(principal.tenantCode());

        return new JwtAuthenticationToken(jwt, principal, principal.getAuthorities());
    }

    private UserPrincipal extractPrincipal(Jwt jwt) {
        String userId = jwt.getSubject();
        String tenantCode = jwt.getClaimAsString(CLAIM_TENANT_CODE);
        String username = jwt.getClaimAsString(CLAIM_USERNAME);
        String email = jwt.getClaimAsString(CLAIM_EMAIL);
        List<String> roles = jwt.getClaimAsStringList(CLAIM_ROLES);

        return new UserPrincipal(userId, tenantCode, username, email, roles);
    }
}
```

### TenantContext Implementation

Thread-local storage for tenant context:

```java
package com.primetech.poc.identity.shared.multitenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Thread-local storage for tenant context.
 *
 * Stores the current tenant's code, schema name, and ID for the request duration.
 * Must be cleared in finally blocks to prevent context leakage.
 */
public final class TenantContext {

    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);

    private static final ThreadLocal<String> CURRENT_TENANT_CODE = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
        // Utility class - prevent instantiation
    }

    /**
     * Set the current tenant context.
     */
    public static void setTenant(String tenantCode, UUID tenantId, String schemaName) {
        log.debug("Setting tenant context: code={}, schema={}", tenantCode, schemaName);
        CURRENT_TENANT_CODE.set(tenantCode);
        CURRENT_TENANT_ID.set(tenantId);
        CURRENT_SCHEMA.set(schemaName);
    }

    /**
     * Set tenant context using code only (derives schema name).
     */
    public static void setTenantCode(String tenantCode) {
        log.debug("Setting tenant code: {}", tenantCode);
        CURRENT_TENANT_CODE.set(tenantCode);
        CURRENT_SCHEMA.set(codeToSchemaName(tenantCode));
    }

    /**
     * Get the current tenant code.
     */
    public static String getTenantCode() {
        return CURRENT_TENANT_CODE.get();
    }

    /**
     * Get the current schema name.
     */
    public static String getSchemaName() {
        return CURRENT_SCHEMA.get();
    }

    /**
     * Get the current tenant ID.
     */
    public static UUID getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    /**
     * Check if a tenant context is set.
     */
    public static boolean isSet() {
        return CURRENT_TENANT_CODE.get() != null;
    }

    /**
     * Clear the tenant context. MUST be called in finally blocks.
     */
    public static void clear() {
        log.debug("Clearing tenant context");
        CURRENT_TENANT_CODE.remove();
        CURRENT_SCHEMA.remove();
        CURRENT_TENANT_ID.remove();
    }

    /**
     * Convert a tenant code to a schema name.
     * E.g., "acme" -> "tenant_acme"
     */
    public static String codeToSchemaName(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Tenant code cannot be null or blank");
        }
        String normalized = code.toLowerCase().replaceAll("[^a-z0-9]", "_");
        return "tenant_" + normalized;
    }

    /**
     * Extract tenant code from schema name.
     * E.g., "tenant_acme" -> "acme"
     */
    public static String schemaNameToCode(String schemaName) {
        if (schemaName == null || !schemaName.startsWith("tenant_")) {
            throw new IllegalArgumentException("Invalid tenant schema name: " + schemaName);
        }
        return schemaName.substring(7);
    }
}
```

### Clean Up After Request

Use a filter or interceptor to ensure `TenantContext` is cleared:

```java
package com.primetech.poc.identity.shared.multitenancy;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter to ensure TenantContext is cleared after each request.
 * Prevents context leakage between requests when threads are reused.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantContextFilter implements Filter {

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            // Always clear context to prevent leakage
            TenantContext.clear();
        }
    }
}
```

---

## 6. Code Examples

### Complete Tenant Entity

```java
package com.primetech.poc.identity.features.tenant.infrastructure;

import com.primetech.poc.identity.features.tenant.domain.TenantStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant JPA Entity.
 *
 * Stored in the public schema as the tenant registry.
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
     * Check if the tenant is active.
     */
    public boolean isActive() {
        return status == TenantStatus.ACTIVE && deletedAt == null;
    }

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
}
```

### Create Tenant Handler

```java
package com.primetech.poc.identity.features.tenant.create;

import com.primetech.poc.identity.features.tenant.domain.TenantStatus;
import com.primetech.poc.identity.features.tenant.infrastructure.TenantEntity;
import com.primetech.poc.identity.features.tenant.infrastructure.TenantRepository;
import com.primetech.poc.identity.shared.exception.BusinessException;
import com.primetech.poc.identity.shared.multitenancy.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * Create Tenant Handler.
 */
@Component
public class CreateTenantHandler {

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
        tenantEntity.setId(UUID.randomUUID());
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
            String sql = String.format(
                "SELECT public.create_tenant_schema('%s')", schemaName);
            connection.createStatement().execute(sql);
            log.debug("Created tenant schema: {}", schemaName);
        } catch (SQLException e) {
            log.error("Failed to create tenant schema: {}", schemaName, e);
            throw new BusinessException("Failed to create tenant schema: " + e.getMessage());
        }
    }

    private void createUserInTenantSchema(
            CreateTenantCommand command, String schemaName) {
        // Implementation creates admin user in tenant schema
        // ...
    }
}
```

### Configuration Properties

```yaml
# application.yaml

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/identity
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      idle-timeout: 30000
      connection-timeout: 20000
      max-lifetime: 1800000
      pool-name: HikariCP-Identity

  jpa:
    hibernate:
      ddl-auto: none  # Liquibase manages schema
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  liquibase:
    enabled: true
    change-log: classpath:/db/db.changelog-master.yaml
    default-schema: public
    liquibase-schema: public
```

---

## 7. Best Practices

### Connection Pooling Considerations

1. **Pool Size**: Calculate based on tenant count and concurrent requests
   ```
   Recommended Pool Size = (Tenants × Concurrent Requests per Tenant) + Buffer
   ```

2. **Schema Switching Overhead**: Setting `search_path` adds minimal overhead (~1ms)

3. **Connection Validation**: Use lightweight query for validation
   ```yaml
   hikari:
     connection-test-query: SELECT 1
   ```

4. **Pool Per Service**: Each service should have its own pool

### Schema Migration Strategy

1. **Use PostgreSQL Functions**: Create a function that generates all tenant tables
   ```sql
   SELECT public.create_tenant_schema('tenant_newcustomer');
   ```

2. **Version Control Migrations**: Store migrations in Liquibase/Flyway

3. **Zero-Downtime Migrations**:
   - Add new columns as nullable first
   - Backfill data in batches
   - Add constraints after backfill

4. **Migration Rollout**:
   ```sql
   -- Loop through all active tenants
   DO $$
   DECLARE
       tenant RECORD;
   BEGIN
       FOR tenant IN SELECT schema_name FROM public.tenants WHERE deleted_at IS NULL
       LOOP
           EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS phone VARCHAR(50)',
               tenant.schema_name);
       END LOOP;
   END $$;
   ```

### Error Handling

1. **Invalid Tenant**: Return 401 Unauthorized
   ```java
   if (!tenantExists(tenantCode)) {
       throw new AuthenticationException("Invalid tenant: " + tenantCode);
   }
   ```

2. **Inactive Tenant**: Return 403 Forbidden
   ```java
   if (!tenantEntity.isActive()) {
       throw new AuthorizationException("Tenant is inactive");
   }
   ```

3. **Missing Context**: Fail fast with clear error
   ```java
   if (!TenantContext.isSet()) {
       throw new IllegalStateException("Tenant context not set");
   }
   ```

---

## 8. Security Considerations

### Tenant Isolation Validation

1. **Always Validate Tenant Context**: Before any database operation
   ```java
   @Override
   public Optional<User> findById(UUID id) {
       if (!TenantContext.isSet()) {
           throw new IllegalStateException("Tenant context required");
       }
       return repository.findById(id);
   }
   ```

2. **Cross-Tenant Query Detection**: Log and alert on suspicious patterns
   ```java
   @Aspect
   @Component
   public class TenantSecurityAspect {

       @Before("execution(* com.primetech..*Repository.*(..))")
       public void checkTenantContext() {
           if (!TenantContext.isSet()) {
               log.error("Repository access without tenant context");
               throw new SecurityException("Tenant context required");
           }
       }
   }
   ```

### Preventing Tenant Leakage

1. **Thread Safety**: Always clear context in finally blocks
   ```java
   try {
       TenantContext.setTenantCode(tenantCode);
       // ... business logic
   } finally {
       TenantContext.clear();  // Critical!
   }
   ```

2. **Async Operations**: Propagate context to async threads
   ```java
   @Async
   public void asyncOperation(String tenantCode) {
       try {
           TenantContext.setTenantCode(tenantCode);
           // ... async work
       } finally {
           TenantContext.clear();
       }
   }
   ```

3. **Schema Name Validation**: Prevent SQL injection in schema names
   ```java
   private boolean isValidSchemaName(String schemaName) {
       return schemaName != null &&
              schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
   }
   ```

### Audit Logging

Log all tenant context changes for security auditing:

```java
public final class TenantContext {

    public static void setTenant(String tenantCode, UUID tenantId, String schemaName) {
        log.info("Tenant context set: code={}, schema={}, thread={}",
            tenantCode, schemaName, Thread.currentThread().getName());
        // ... set values
    }

    public static void clear() {
        log.info("Tenant context cleared: thread={}",
            Thread.currentThread().getName());
        // ... clear values
    }
}
```

---

## 9. Testing Multi-Tenant Code

### Unit Testing with Mock Tenant Context

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenant("test_tenant", UUID.randomUUID(), "tenant_test_tenant");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldFindUserById() {
        // Given
        UUID userId = UUID.randomUUID();
        User expected = new User(userId, "testuser");

        when(userRepository.findById(userId)).thenReturn(Optional.of(expected));

        // When
        Optional<User> result = userService.findById(userId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("testuser");
    }
}
```

### Integration Testing with TestContainers

```java
@SpringBootTest
@Testcontainers
class TenantIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("identity_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldIsolateTenantData() {
        // Create two tenants
        createTenant("tenant_a");
        createTenant("tenant_b");

        // Create user in tenant_a
        TenantContext.setTenantCode("tenant_a");
        createUser("user_a");

        // Verify user not visible in tenant_b
        TenantContext.setTenantCode("tenant_b");
        assertThat(userRepository.findByUsername("user_a")).isEmpty();

        // Cleanup
        TenantContext.clear();
    }
}
```

### Testing Schema Switching

```java
@Test
void shouldSwitchSchemaCorrectly() throws SQLException {
    // Given
    String tenantCode = "acme";
    String expectedSchema = "tenant_acme";

    // When
    TenantContext.setTenantCode(tenantCode);
    Connection connection = connectionProvider.getConnection(expectedSchema);

    // Then
    try (var stmt = connection.createStatement();
         var rs = stmt.executeQuery("SHOW search_path")) {
        rs.next();
        String searchPath = rs.getString(1);
        assertThat(searchPath).contains(expectedSchema);
    }

    // Cleanup
    TenantContext.clear();
    connection.close();
}
```

---

## 10. Troubleshooting

### Common Issues and Solutions

#### 1. "Tenant context not set" Error

**Symptom**: `IllegalStateException: Tenant context not set`

**Cause**: Request reaches repository without tenant context

**Solution**:
- Check JWT filter is configured correctly
- Verify `TenantContextFilter` is registered
- Ensure context is set before any repository access

```java
// Debug: Log context status
log.debug("Tenant context status: isSet={}", TenantContext.isSet());
```

#### 2. Cross-Tenant Data Access

**Symptom**: User sees data from another tenant

**Cause**: Tenant context not cleared between requests (thread reuse)

**Solution**:
- Always clear context in finally block
- Add `TenantContextFilter` with highest precedence

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantContextFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();  // Always clear!
        }
    }
}
```

#### 3. Schema Not Found Error

**Symptom**: `PSQLException: schema "tenant_xxx" does not exist`

**Cause**: Tenant schema was not created

**Solution**:
- Call `create_tenant_schema` function during tenant creation
- Verify function exists in database

```sql
-- Check function exists
SELECT proname FROM pg_proc WHERE proname = 'create_tenant_schema';

-- Manually create schema for testing
SELECT public.create_tenant_schema('tenant_test');
```

#### 4. Hibernate DDL Attempts

**Symptom**: Hibernate tries to create/drop tables

**Cause**: `hibernate.hbm2ddl.auto` not set to `none`

**Solution**:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none  # Required for multi-tenant with Liquibase
```

#### 5. Connection Pool Exhaustion

**Symptom**: `SQLTransientConnectionException: Connection is not available`

**Cause**: Connections not returned to pool

**Solution**:
- Verify `releaseConnection` is called
- Check for unclosed connections in manual JDBC code
- Increase pool size temporarily while investigating

```yaml
hikari:
  maximum-pool-size: 20  # Increase if needed
  leak-detection-threshold: 60000  # Enable leak detection
```

### Debug Logging

Enable debug logging for multi-tenancy:

```yaml
logging:
  level:
    com.primetech.poc.identity.shared.multitenancy: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    com.zaxxer.hikari: DEBUG
```

### Verification Queries

```sql
-- List all tenant schemas
SELECT schema_name FROM information_schema.schemata
WHERE schema_name LIKE 'tenant_%';

-- Check current search_path
SHOW search_path;

-- List tables in a tenant schema
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'tenant_acme';

-- View tenant registry
SELECT code, schema_name, status FROM public.tenants;
```

---

## Summary

Schema-based multi-tenancy in Spring Boot 4.x with Hibernate 7.x requires:

1. **Database Layer**: PostgreSQL schemas with registry in `public`
2. **Hibernate Layer**: Custom `MultiTenantConnectionProvider` and `CurrentTenantIdentifierResolver`
3. **Spring Layer**: Programmatic `EntityManagerFactory` configuration
4. **Application Layer**: Thread-local `TenantContext` with proper cleanup

The key to success is ensuring tenant context is always set before database access and always cleared after request completion.

---

## References

- [Hibernate Multi-Tenancy Documentation](https://docs.jboss.org/hibernate/orm/7.0/userguide/html_single/Hibernate_User_Guide.html#multitenacy)
- [PostgreSQL Schemas](https://www.postgresql.org/docs/current/ddl-schemas.html)
- [Spring Boot Data JPA](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.sql.jpa-and-spring-data)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
