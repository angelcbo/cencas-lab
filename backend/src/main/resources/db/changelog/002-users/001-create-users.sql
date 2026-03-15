-- Sprint 1 — users table
-- Depends on: tenants (001-tenants)
--
-- tenant_id is NULL for SUPER_ADMIN users (platform-level, not tied to a lab).
--
-- Email uniqueness rules:
--   - For tenant-scoped users: (tenant_id, email) must be unique.
--   - For SUPER_ADMINs: email must be globally unique among SUPER_ADMINs.
--   In PostgreSQL a UNIQUE constraint on (tenant_id, email) does NOT prevent
--   two NULLs with the same email (NULL != NULL). We use partial indexes instead.
--
-- Role is enforced with a CHECK constraint. Adding PATIENT post-MVP requires
-- a new changeset to widen the constraint.

CREATE TABLE users (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_users         PRIMARY KEY (id),
    CONSTRAINT fk_users_tenant  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT chk_users_role   CHECK (role IN (
        'SUPER_ADMIN',
        'LAB_ADMIN',
        'LAB_RECEPTIONIST',
        'LAB_ANALYST',
        'LAB_DOCTOR'
    ))
);

-- Tenant-scoped users: (tenant_id, email) unique within a lab
CREATE UNIQUE INDEX uq_users_tenant_email
    ON users (tenant_id, email)
    WHERE tenant_id IS NOT NULL;

-- SUPER_ADMIN users: email must be globally unique among platform admins
CREATE UNIQUE INDEX uq_users_superadmin_email
    ON users (email)
    WHERE tenant_id IS NULL;

-- Support fast lookups by tenant (list users in a lab, Hibernate filter)
CREATE INDEX idx_users_tenant_id ON users (tenant_id);

COMMENT ON TABLE  users               IS 'Application users. tenant_id IS NULL indicates a SUPER_ADMIN.';
COMMENT ON COLUMN users.tenant_id     IS 'NULL for SUPER_ADMIN. FK to tenants for all lab users.';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash (strength 12). Never store plaintext.';
COMMENT ON COLUMN users.role          IS 'Single role per user. See CLAUDE.md for role capabilities.';
