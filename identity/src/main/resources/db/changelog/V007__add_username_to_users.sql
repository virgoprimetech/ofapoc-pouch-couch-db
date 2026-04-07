-- liquibase formatted sql

-- changeset identity:007-add-username-to-users
-- Add username column to users table
ALTER TABLE users ADD COLUMN username VARCHAR(50);

-- Update existing records to generate usernames from their emails
-- Extract the local part of the email (before @) and sanitize it
UPDATE users
SET username = REGEXP_REPLACE(
    SPLIT_PART(email, '@', 1),
    '[^a-zA-Z0-9_-]',
    '',
    'g'
)
WHERE username IS NULL;

-- Handle potential duplicates by appending a number if needed
-- This uses a CTE to identify and resolve duplicates
WITH duplicate_usernames AS (
    SELECT id, username,
           ROW_NUMBER() OVER (PARTITION BY username ORDER BY created_at) as rn
    FROM users
    WHERE deleted_at IS NULL
)
UPDATE users u
SET username = d.username || CASE WHEN d.rn > 1 THEN '_' || (d.rn - 1)::text ELSE '' END
FROM duplicate_usernames d
WHERE u.id = d.id AND d.rn > 1;

-- Make username NOT NULL after populating
ALTER TABLE users ALTER COLUMN username SET NOT NULL;

-- Add unique constraint on username
ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE (username);

-- Create index for username lookups (filtered for non-deleted records)
CREATE INDEX idx_users_username ON users(username) WHERE deleted_at IS NULL;

-- changeset identity:007-add-username-comments
COMMENT ON COLUMN users.username IS 'Unique username for authentication (3-50 chars, alphanumeric with underscores and hyphens)';
