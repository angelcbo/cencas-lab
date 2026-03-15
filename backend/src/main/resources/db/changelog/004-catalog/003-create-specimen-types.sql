-- Sprint 3 — catalog_specimen_types table
-- The type of biological material required for a test.
-- Examples: whole blood, serum, plasma, urine, CSF.

CREATE TABLE catalog_specimen_types (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_catalog_specimen_types        PRIMARY KEY (id),
    CONSTRAINT fk_catalog_specimen_types_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_catalog_specimen_types_code   UNIQUE (tenant_id, code)
);

CREATE INDEX idx_catalog_specimen_types_tenant         ON catalog_specimen_types (tenant_id);
CREATE INDEX idx_catalog_specimen_types_tenant_created ON catalog_specimen_types (tenant_id, created_at);

COMMENT ON TABLE  catalog_specimen_types      IS 'Biological specimen required for a test (e.g. blood, serum, plasma, urine).';
COMMENT ON COLUMN catalog_specimen_types.code IS 'Short lab code, unique within tenant (e.g. BLOOD, SERUM, URINE).';
