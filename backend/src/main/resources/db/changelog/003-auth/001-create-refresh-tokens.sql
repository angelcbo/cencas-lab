-- Sprint 1 — refresh_tokens table
-- Depends on: tenants (001), users (002)
--
-- Refresh token strategy (see CLAUDE.md Security Design):
--   - Token value is an opaque UUID, NEVER a JWT.
--   - Only the SHA-256 hex hash (64 chars) is stored in DB.
--   - The raw token is delivered to the client via httpOnly cookie only.
--   - Rotation: on each /refresh call the old row is revoked and a new row inserted.
--   - Family tracking: all tokens from the same login share family_id.
--   - Reuse detection: if a revoked token is presented, revoke the entire family.
--   - replaced_by: self-referential FK forms an audit chain of rotations.

CREATE TABLE refresh_tokens (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    token_hash   VARCHAR(64)  NOT NULL,
    user_id      UUID         NOT NULL,
    tenant_id    UUID,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked      BOOLEAN      NOT NULL DEFAULT false,
    family_id    UUID         NOT NULL,
    replaced_by  UUID,
    ip_address   VARCHAR(45),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_refresh_tokens            PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash       UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user       FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_tenant     FOREIGN KEY (tenant_id)
        REFERENCES tenants(id),
    CONSTRAINT fk_refresh_tokens_replaced   FOREIGN KEY (replaced_by)
        REFERENCES refresh_tokens(id)
);

-- Fast lookup by user when revoking all tokens for a user
CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens (user_id);

-- Fast revocation of an entire token family (reuse detection)
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);

-- token_hash lookup on every /refresh call (covered by the UNIQUE constraint index)

COMMENT ON TABLE  refresh_tokens             IS 'DB-backed opaque refresh tokens. SHA-256 hash stored, never raw value.';
COMMENT ON COLUMN refresh_tokens.token_hash  IS 'SHA-256 hex of the raw UUID token. 64 hex chars.';
COMMENT ON COLUMN refresh_tokens.family_id   IS 'All tokens from the same login share this ID. Used for reuse-detection revocation.';
COMMENT ON COLUMN refresh_tokens.replaced_by IS 'Set on rotation: points to the successor token. Forms an audit chain.';
COMMENT ON COLUMN refresh_tokens.tenant_id   IS 'Denormalized from user for faster revocation queries. NULL for SUPER_ADMIN.';
