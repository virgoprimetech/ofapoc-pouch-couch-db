-- liquibase formatted sql

-- changeset identity:002-create-roles-table
CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    code            VARCHAR(50) NOT NULL UNIQUE,
    display_name    VARCHAR(100) NOT NULL,
    scope           VARCHAR(20) NOT NULL,
    is_system       BOOLEAN NOT NULL DEFAULT FALSE,
    description     TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE
);

-- Create indexes
CREATE INDEX idx_roles_code ON roles(code) WHERE deleted_at IS NULL;
CREATE INDEX idx_roles_scope ON roles(scope) WHERE deleted_at IS NULL;

-- changeset identity:002-add-roles-comments
COMMENT ON TABLE roles IS 'Role definitions with scope support (GLOBAL, CHAIN, PROPERTY)';
COMMENT ON COLUMN roles.code IS 'Unique role code (e.g., SYSTEM_ADMIN, PROPERTY_MANAGER)';
COMMENT ON COLUMN roles.scope IS 'Role scope: GLOBAL, CHAIN, or PROPERTY';
COMMENT ON COLUMN roles.is_system IS 'System roles cannot be deleted';
