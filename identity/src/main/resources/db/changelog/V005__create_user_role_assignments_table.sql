-- liquibase formatted sql

-- changeset identity:005-create-user-role-assignments-table
CREATE TABLE user_role_assignments (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    property_id     UUID,
    chain_id        UUID,
    valid_from      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    valid_until     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE
);

-- Create indexes
CREATE UNIQUE INDEX ux_user_role_property_active ON user_role_assignments (user_id, role_id, COALESCE(property_id, '00000000-0000-0000-0000-000000000000'::uuid)) WHERE deleted_at IS NULL;

CREATE INDEX idx_user_role_assignments_user ON user_role_assignments(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_role_assignments_role ON user_role_assignments(role_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_role_assignments_property ON user_role_assignments(property_id) WHERE deleted_at IS NULL AND property_id IS NOT NULL;
CREATE INDEX idx_user_role_assignments_chain ON user_role_assignments(chain_id) WHERE deleted_at IS NULL AND chain_id IS NOT NULL;

-- changeset identity:005-add-user-role-assignments-comments
COMMENT ON TABLE user_role_assignments IS 'User role assignments with property/chain scope support';
COMMENT ON COLUMN user_role_assignments.property_id IS 'Property scope for PROPERTY-level roles';
COMMENT ON COLUMN user_role_assignments.chain_id IS 'Chain scope for CHAIN-level roles';
COMMENT ON COLUMN user_role_assignments.valid_from IS 'Assignment validity start time';
COMMENT ON COLUMN user_role_assignments.valid_until IS 'Assignment validity end time (NULL = no expiry)';
COMMENT ON COLUMN user_role_assignments.deleted_at IS 'Soft delete timestamp - NULL means active';
