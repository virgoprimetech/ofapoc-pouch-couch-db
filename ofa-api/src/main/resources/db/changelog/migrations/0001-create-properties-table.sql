-- liquibase formatted sql

-- changeset ofa:0001-create-properties-table labels:v1.0
-- rollback DROP TABLE IF EXISTS properties;

CREATE TABLE properties (
    id              UUID PRIMARY KEY,
    couchdb_rev     VARCHAR(100),
    name            VARCHAR(255) NOT NULL,
    property_type   VARCHAR(30) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    description     TEXT,
    address_line1   VARCHAR(500),
    address_line2   VARCHAR(500),
    city            VARCHAR(255),
    state_province  VARCHAR(255),
    postal_code     VARCHAR(20),
    country         VARCHAR(2),
    latitude        DECIMAL(10, 8),
    longitude       DECIMAL(11, 8),
    phone           VARCHAR(50),
    email           VARCHAR(255),
    website         VARCHAR(500),
    timezone        VARCHAR(50),
    currency        VARCHAR(3),
    total_rooms     INTEGER,
    tenant_id       VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_properties_tenant_id ON properties (tenant_id);
CREATE INDEX idx_properties_status ON properties (status);
CREATE INDEX idx_properties_deleted_at ON properties (deleted_at);
CREATE INDEX idx_properties_tenant_status ON properties (tenant_id, status);
