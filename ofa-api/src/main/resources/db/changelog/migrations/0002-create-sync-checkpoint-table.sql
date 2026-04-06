-- liquibase formatted sql

-- changeset ofa:0002-create-sync-checkpoint-table labels:v1.0
-- rollback DROP TABLE IF EXISTS couchdb_sync_checkpoint;

CREATE TABLE couchdb_sync_checkpoint (
    id          VARCHAR(100) PRIMARY KEY,
    last_seq    VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
