-- liquibase formatted sql

-- changeset identity:008-create-tenants-table-public
-- Create tenants table in public schema for tenant registry

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

-- Create indexes for tenant lookups
CREATE INDEX idx_tenants_code ON public.tenants(code) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_schema_name ON public.tenants(schema_name) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_status ON public.tenants(status) WHERE deleted_at IS NULL;

-- changeset identity:008-add-tenants-comments
COMMENT ON TABLE public.tenants IS 'Tenant registry - each tenant has its own PostgreSQL schema';
COMMENT ON COLUMN public.tenants.id IS 'Tenant ID (same as code)';
COMMENT ON COLUMN public.tenants.code IS 'Unique tenant code used in login (e.g., "acme")';
COMMENT ON COLUMN public.tenants.schema_name IS 'PostgreSQL schema name (e.g., "tenant_acme")';
COMMENT ON COLUMN public.tenants.display_name IS 'Human-readable tenant name (e.g., "Acme Hotels")';
COMMENT ON COLUMN public.tenants.status IS 'Tenant status: ACTIVE, SUSPENDED, DEACTIVATED';
COMMENT ON COLUMN public.tenants.deleted_at IS 'Soft delete timestamp - NULL means active';

-- changeset identity:008-insert-default-public-tenant
-- Insert default public tenant for system administration and initial setup
-- This tenant uses the existing public schema and serves as the default tenant for:
-- - Initial system setup and bootstrapping
-- - Super admin users who manage all tenants
-- - Migration from single-tenant to multi-tenant architecture

INSERT INTO public.tenants (id, code, schema_name, display_name, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'public',
    'public',
    'Default Tenant',
    'ACTIVE',
    NOW(),
    NOW()
);

-- changeset identity:008-add-default-tenant-comment
COMMENT ON COLUMN public.tenants.id IS 'Tenant ID (same as code for simplicity, e.g., "public", "acme")';
