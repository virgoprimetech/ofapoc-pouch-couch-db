-- liquibase formatted sql

-- changeset identity:010-create-signing-keys-table
-- Create signing_keys table for persistent RSA key storage with rotation support
-- Keys are stored in the public schema since they're shared across all tenants

CREATE TABLE IF NOT EXISTS public.signing_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_id          VARCHAR(255) NOT NULL UNIQUE,
    key_type        VARCHAR(50) NOT NULL DEFAULT 'RSA',
    algorithm       VARCHAR(50) NOT NULL DEFAULT 'RS256',
    public_key      TEXT NOT NULL,
    private_key     TEXT NOT NULL,  -- Encrypted at rest with AES-GCM
    key_size        INTEGER NOT NULL DEFAULT 2048,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE,
    rotated_at      TIMESTAMP WITH TIME ZONE
);

-- Create indexes for key lookups
CREATE INDEX idx_signing_keys_status ON public.signing_keys(status);
CREATE INDEX idx_signing_keys_key_id ON public.signing_keys(key_id);

-- changeset identity:010-add-signing-keys-comments
COMMENT ON TABLE public.signing_keys IS 'RSA key pairs for JWT signing with rotation support';
COMMENT ON COLUMN public.signing_keys.id IS 'Primary key for the signing key record';
COMMENT ON COLUMN public.signing_keys.key_id IS 'JWK Key ID (kid) - unique identifier for the key';
COMMENT ON COLUMN public.signing_keys.key_type IS 'Key type: RSA (currently only RSA supported)';
COMMENT ON COLUMN public.signing_keys.algorithm IS 'Signing algorithm: RS256';
COMMENT ON COLUMN public.signing_keys.public_key IS 'Public key in JWK JSON format (exposed via JWKS)';
COMMENT ON COLUMN public.signing_keys.private_key IS 'Private key in JWK JSON format (encrypted at rest)';
COMMENT ON COLUMN public.signing_keys.key_size IS 'Key size in bits (2048 or 4096)';
COMMENT ON COLUMN public.signing_keys.status IS 'Key status: ACTIVE (signing), DEPRECATED (verification only), REVOKED (no longer valid)';
COMMENT ON COLUMN public.signing_keys.created_at IS 'When the key was created';
COMMENT ON COLUMN public.signing_keys.expires_at IS 'Optional expiration date for key rotation planning';
COMMENT ON COLUMN public.signing_keys.rotated_at IS 'When this key was rotated from ACTIVE to DEPRECATED';
