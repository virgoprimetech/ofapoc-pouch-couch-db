-- liquibase formatted sql

-- changeset identity:004-create-role-permissions-table
CREATE TABLE role_permissions (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(role_id, permission_id)
);

-- Create indexes
CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);

-- changeset identity:004-add-role-permissions-comments
COMMENT ON TABLE role_permissions IS 'Junction table for many-to-many role-permission relationship';
