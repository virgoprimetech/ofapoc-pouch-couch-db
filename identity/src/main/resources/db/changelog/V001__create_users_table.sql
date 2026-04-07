-- liquibase formatted sql

-- changeset identity:001-create-users-table
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    phone           VARCHAR(50),
    preferred_language VARCHAR(10) DEFAULT 'en',
    timezone        VARCHAR(50) DEFAULT 'UTC',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE
);

-- Create indexes for common queries
CREATE INDEX idx_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_status ON users(status) WHERE deleted_at IS NULL;

-- changeset identity:001-add-users-audit-comment
COMMENT ON TABLE users IS 'User accounts with soft delete support';
COMMENT ON COLUMN users.id IS 'UUIDv7 client-generated ID for offline-first sync';
COMMENT ON COLUMN users.status IS 'User status: PENDING, ACTIVE, SUSPENDED, DEACTIVATED';
COMMENT ON COLUMN users.deleted_at IS 'Soft delete timestamp - NULL means active';
