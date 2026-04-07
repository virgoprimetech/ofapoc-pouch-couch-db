-- liquibase formatted sql

-- changeset identity:003-create-permissions-table
CREATE TABLE permissions (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    code            VARCHAR(100) NOT NULL UNIQUE,
    resource        VARCHAR(50) NOT NULL,
    action          VARCHAR(50) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes
CREATE INDEX idx_permissions_code ON permissions(code);
CREATE INDEX idx_permissions_resource ON permissions(resource);
CREATE UNIQUE INDEX idx_permissions_resource_action ON permissions(resource, action);

-- changeset identity:003-add-permissions-comments
COMMENT ON TABLE permissions IS 'Permission definitions (resource:action format)';
COMMENT ON COLUMN permissions.code IS 'Unique permission code (e.g., property:read, reservation:create)';
COMMENT ON COLUMN permissions.resource IS 'Resource name (e.g., property, reservation, guest)';
COMMENT ON COLUMN permissions.action IS 'Action name (e.g., read, create, update, delete)';
