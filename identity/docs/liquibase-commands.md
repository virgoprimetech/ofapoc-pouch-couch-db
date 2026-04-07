# Liquibase Commands Reference

This document provides a comprehensive reference for working with Liquibase database migrations in the Identity service.

## Overview

The Identity service uses Liquibase for database schema management and version control. Liquibase runs automatically on application startup, applying any pending migrations before the application becomes available.

### Configuration

| Setting | Value |
|---------|-------|
| Changelog file | `src/main/resources/db/db.changelog-master.yaml` |
| Migration format | Formatted SQL |
| Default schema | `public` |
| Liquibase tables schema | `public` |
| Database | PostgreSQL 18+ |

### Changelog Structure

```
src/main/resources/db/
├── db.changelog-master.yaml    # Master changelog (includes all migrations)
└── changelog/
    ├── V001__create_users_table.sql
    ├── V002__create_roles_table.sql
    ├── V003__create_permissions_table.sql
    ├── V004__create_role_permissions_table.sql
    ├── V005__create_user_role_assignments_table.sql
    ├── V006__seed_default_roles_permissions.sql
    ├── V007__add_username_to_users.sql
    ├── V008__create_tenants_table_public.sql
    └── V009__create_tenant_schema_function.sql
```

## Prerequisites

Before running Liquibase commands, ensure the following requirements are met:

1. **PostgreSQL is running** on `localhost:5432`
2. **Database exists**: Create the `identity` database if it does not exist
3. **Database credentials**: `postgres/postgres` (default)

### Create the Database

```bash
# Connect to PostgreSQL as superuser
pssql -U postgres

# Create the identity database
CREATE DATABASE identity;

# Exit psql
\q
```

Or using a single command:

```bash
psql -U postgres -c "CREATE DATABASE identity;"
```

## Common Commands

All commands should be run from the `/Users/devvirgo/Workspace/primetech/poc/ofa-poc-workspace/identity` directory.

### Running Migrations (Update)

#### Run all pending migrations

Liquibase runs automatically when you start the application:

```bash
./mvnw spring-boot:run
```

To run migrations without starting the application, use the Liquibase Maven plugin:

```bash
./mvnw liquibase:update
```

#### Run with specific properties

```bash
./mvnw liquibase:update \
  -Dliquibase.url=jdbc:postgresql://localhost:5432/identity \
  -Dliquibase.username=postgres \
  -Dliquibase.password=postgres
```

### Checking Status

#### List pending changesets

```bash
./mvnw liquibase:status
```

Output shows which changesets have not been applied to the database.

#### Show migration history

```bash
./mvnw liquibase:history
```

This displays all previously applied changesets in execution order.

#### List all changesets (applied and pending)

```bash
./mvnw liquibase:listLocks
```

Shows any active locks on the database that might prevent migrations.

### Generating SQL Preview

Preview the SQL that Liquibase will execute without actually running it. This is useful for code reviews and validation.

#### Preview all pending migrations

```bash
./mvnw liquibase:futureRollbackSQL
```

#### Preview SQL for specific changesets

```bash
./mvnw liquibase:updateSQL
```

This outputs the SQL to stdout. Redirect to a file for review:

```bash
./mvnw liquibase:updateSQL > /tmp/migration-preview.sql
```

#### Generate rollback SQL for last N changesets

```bash
./mvnw liquibase:rollbackSQL -Dliquibase.rollbackCount=1
```

### Rollback Commands

Rollbacks revert database changes. Use with caution in production environments.

#### Rollback last N changesets

```bash
./mvnw liquibase:rollback -Dliquibase.rollbackCount=1
```

#### Rollback to a specific tag

First, create a tag before making changes:

```bash
./mvnw liquibase:tag -Dliquibase.tag=before-feature-x
```

Then rollback to that tag:

```bash
./mvnw liquibase:rollback -Dliquibase.tag=before-feature-x
```

#### Rollback to a specific date/time

```bash
./mvnw liquibase:rollback -Dliquibase.rollbackDate="2024-03-25T10:00:00"
```

#### Rollback everything (reset to empty state)

```bash
./mvnw liquibase:rollback -Dliquibase.rollbackCount=9999
```

### Clearing Checksums

When you modify an already-executed changeset, Liquibase will fail with a checksum validation error. Clear checksums to force re-calculation.

```bash
./mvnw liquibase:clearChecksums
```

After clearing checksums, run update to recalculate:

```bash
./mvnw liquibase:update
```

### Dropping All Objects (Development Only)

Completely reset the database by dropping all tables, indexes, and other objects.

> **Warning:** This command destroys all data. Never use in production.

```bash
./mvnw liquibase:dropAll
```

To also drop the public schema:

```bash
./mvnw liquibase:dropAll -Dliquibase.schemas=public
```

After dropping, run migrations again:

```bash
./mvnw liquibase:update
```

### Releasing Locks

If a migration was interrupted or crashed, you may need to manually release the lock:

```bash
./mvnw liquibase:releaseLocks
```

### Validate Changelog

Check for syntax errors in changelog files before running:

```bash
./mvnw liquibase:validate
```

## Schema Configuration

### Multi-Tenancy Architecture

The Identity service uses a **schema-per-tenant** architecture:

| Schema | Purpose |
|--------|---------|
| `public` | Tenant registry (`tenants` table) and Liquibase control tables |
| `tenant_{id}` | Individual tenant schemas containing users, roles, permissions |

### Liquibase Control Tables

Liquibase creates two tables in the `public` schema:

- **DATABASECHANGELOG**: Tracks which changesets have been applied
- **DATABASECHANGELOGLOCK**: Prevents concurrent migrations

### Application Configuration

The `application.yaml` configures Liquibase to use the `public` schema:

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:/db/db.changelog-master.yaml
    default-schema: public
    liquibase-schema: public
```

### Creating New Migrations

When adding new migrations for multi-tenant tables, specify the target schema in the SQL file:

```sql
-- liquibase formatted sql

-- changeset identity:010-create-tenant-table
-- This table goes in the public schema
CREATE TABLE public.example_table (
    id UUID PRIMARY KEY
);

-- changeset identity:010-create-tenant-specific-table
-- This would be applied to tenant schemas by application code
-- CREATE TABLE tenant_table (...)
```

## Troubleshooting

### "classpath does not exist" Error

**Symptom:**

```
Error: The file 'db/changelog/V010__new_migration.sql' was not found in the classpath
```

**Cause:** The changelog file references a migration that does not exist or is not on the classpath.

**Solution:**

1. Verify the file exists in the correct location:

   ```bash
   ls -la src/main/resources/db/changelog/
   ```

2. Ensure the filename matches exactly (case-sensitive)

3. Rebuild the project to update the classpath:

   ```bash
   ./mvnw clean compile
   ```

### Checksum Validation Errors

**Symptom:**

```
Error: Validation Failed: "1 change sets had a different checksum than expected"
```

**Cause:** A changeset that was already applied to the database has been modified.

**Solutions:**

1. **Recommended:** Create a new changeset instead of modifying an existing one

2. **For development only:** Clear checksums and re-run:

   ```bash
   ./mvnw liquibase:clearChecksums
   ./mvnw liquibase:update
   ```

3. **For production:** Never clear checksums. Create a corrective changeset.

### Schema Not Found Errors

**Symptom:**

```
Error: schema "tenant_xxx" does not exist
```

**Cause:** Attempting to access a tenant schema that has not been created.

**Solution:**

Tenant schemas are created automatically by the application using the function defined in `V009__create_tenant_schema_function.sql`. Ensure the tenant has been properly registered in the `tenants` table.

### Migration Lock Issues

**Symptom:**

```
Error: Could not acquire change log lock
```

**Cause:** A previous migration was interrupted, leaving the lock in place.

**Solution:**

```bash
./mvnw liquibase:releaseLocks
```

Then retry the migration:

```bash
./mvnw liquibase:update
```

### Connection Refused Errors

**Symptom:**

```
Error: Connection to localhost:5432 refused
```

**Cause:** PostgreSQL is not running or not accessible.

**Solution:**

1. Verify PostgreSQL is running:

   ```bash
   pg_isready -h localhost -p 5432
   ```

2. Check connection parameters in `application.yaml`

3. Verify the database exists:

   ```bash
   psql -U postgres -l | grep identity
   ```

### Wrong Database Selected

**Symptom:**

```
Error: relation "users" does not exist
```

**Cause:** Liquibase connected to the wrong database.

**Solution:**

Verify the connection URL includes the database name:

```bash
./mvnw liquibase:update \
  -Dliquibase.url=jdbc:postgresql://localhost:5432/identity
```

## Best Practices

### Naming Conventions

Follow these conventions for migration files:

```
V{number}__{description}.sql

Examples:
V001__create_users_table.sql
V002__add_email_index_to_users.sql
V003__seed_admin_user.sql
```

- Use sequential numbering (001, 002, 003...)
- Use double underscore (`__`) to separate version from description
- Use snake_case for descriptions
- Keep descriptions short and meaningful

### Changeset IDs

Use descriptive changeset IDs in SQL files:

```sql
-- changeset identity:001-create-users-table
```

Format: `{author}:{version}-{description}`

### Always Test Rollbacks

Before deploying to production, verify rollback works:

```bash
# Generate rollback SQL and review
./mvnw liquibase:rollbackSQL -Dliquibase.rollbackCount=1

# Test rollback
./mvnw liquibase:rollback -Dliquibase.rollbackCount=1

# Re-apply
./mvnw liquibase:update
```

### Never Modify Applied Changesets

Once a changeset has been applied to any environment:

1. **Do not modify it** - Create a new changeset instead
2. **Do not rename it** - The filename is part of the checksum
3. **Do not delete it** - Other environments may need to apply it

### Use Transactions Wisely

Liquibase wraps each changeset in a transaction by default. For operations that cannot run in a transaction (like `CREATE INDEX CONCURRENTLY`), disable it:

```sql
-- changeset identity:010-create-concurrent-index
-- runOnChange: false
-- runInTransaction: false
CREATE INDEX CONCURRENTLY idx_users_email_lower ON users(LOWER(email));
```

### Tag Before Major Changes

Create a tag before deploying major changes:

```bash
./mvnw liquibase:tag -Dliquibase.tag=release-1.0.0
```

This makes rollback easier if issues arise.

### Review Generated SQL

Always review the SQL that will be executed before running migrations in production:

```bash
./mvnw liquibase:updateSQL > review.sql
```

### Keep Migrations Small

Each migration should do one thing well:

- **Good:** One table creation per file
- **Good:** One index addition per file
- **Avoid:** Multiple unrelated changes in one file

This makes rollbacks safer and debugging easier.

### Use Idempotent Operations Where Possible

Make migrations re-runnable using `IF EXISTS` and `IF NOT EXISTS`:

```sql
-- Good: Idempotent
CREATE TABLE IF NOT EXISTS users (...);
DROP TABLE IF EXISTS old_table;

-- Avoid: Will fail if run twice
CREATE TABLE users (...);
```

### Document Complex Migrations

Add comments explaining non-obvious logic:

```sql
-- changeset identity:015-complex-data-migration
-- Migration: Split full_name into first_name and last_name
-- This is a one-time migration for existing data

UPDATE users
SET first_name = SPLIT_PART(full_name, ' ', 1),
    last_name = SUBSTRING(full_name FROM POSITION(' ' IN full_name) + 1)
WHERE full_name IS NOT NULL AND full_name LIKE '% %';
```

## Quick Reference

| Task | Command |
|------|---------|
| Run migrations | `./mvnw liquibase:update` |
| Check status | `./mvnw liquibase:status` |
| View history | `./mvnw liquibase:history` |
| Preview SQL | `./mvnw liquibase:updateSQL` |
| Rollback 1 changeset | `./mvnw liquibase:rollback -Dliquibase.rollbackCount=1` |
| Clear checksums | `./mvnw liquibase:clearChecksums` |
| Release locks | `./mvnw liquibase:releaseLocks` |
| Drop all (dev only) | `./mvnw liquibase:dropAll` |
| Validate changelog | `./mvnw liquibase:validate` |
| Create tag | `./mvnw liquibase:tag -Dliquibase.tag=name` |

## Related Documentation

- [Liquibase Official Documentation](https://www.liquibase.org/documentation.html)
- [Liquibase Maven Plugin](https://www.liquibase.org/documentation/maven/index.html)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/current/index.html)
