-- Sprint 3 — catalog_techniques table
-- The analytical method used to perform a measurement.
-- Examples: spectrophotometry, ELISA, PCR, nephelometry.

CREATE TABLE catalog_techniques (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_catalog_techniques        PRIMARY KEY (id),
    CONSTRAINT fk_catalog_techniques_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_catalog_techniques_code   UNIQUE (tenant_id, code)
);

CREATE INDEX idx_catalog_techniques_tenant         ON catalog_techniques (tenant_id);
CREATE INDEX idx_catalog_techniques_tenant_created ON catalog_techniques (tenant_id, created_at);

COMMENT ON TABLE  catalog_techniques      IS 'Analytical method used to measure a test (e.g. spectrophotometry, ELISA, PCR).';
COMMENT ON COLUMN catalog_techniques.code IS 'Short lab code, unique within tenant (e.g. SPECTRO, ELISA, PCR).';
