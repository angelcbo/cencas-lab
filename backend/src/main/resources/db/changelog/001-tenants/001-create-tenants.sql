-- Sprint 1 — tenants table
-- No foreign keys to other application tables (root of the ownership hierarchy).
-- gen_random_uuid() is built-in from PostgreSQL 13+; no extension needed.

CREATE TABLE tenants (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    slug        VARCHAR(63)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    timezone    VARCHAR(50)  NOT NULL DEFAULT 'America/Mexico_City',
    tax_rate    NUMERIC(5,4) NOT NULL DEFAULT 0.1600,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_tenants      PRIMARY KEY (id),
    CONSTRAINT uq_tenants_slug UNIQUE (slug)
);

COMMENT ON TABLE  tenants            IS 'One row per lab tenant. Root of all tenant-scoped data.';
COMMENT ON COLUMN tenants.slug       IS 'URL-safe identifier, e.g. "laboratorio-central". Immutable after creation.';
COMMENT ON COLUMN tenants.tax_rate   IS 'IVA rate as a decimal, e.g. 0.1600 for 16%. Stored here so invoices snapshot the rate at order time.';
COMMENT ON COLUMN tenants.timezone   IS 'IANA timezone string used for display formatting only. All stored timestamps are UTC.';
