-- Sprint 3 — catalog_analytes table
-- The smallest measurable laboratory component (e.g. glucose, sodium, hemoglobin).
-- Tenant-scoped: each lab owns its own analyte catalog.
-- result_type drives how results are entered and auto-flagged in Sprint 5.

CREATE TABLE catalog_analytes (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    default_unit VARCHAR(50),
    result_type  VARCHAR(20)  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_catalog_analytes        PRIMARY KEY (id),
    CONSTRAINT fk_catalog_analytes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_catalog_analytes_code   UNIQUE (tenant_id, code),
    CONSTRAINT chk_catalog_analytes_rt    CHECK (result_type IN ('NUMERIC', 'TEXT', 'QUALITATIVE'))
);

CREATE INDEX idx_catalog_analytes_tenant         ON catalog_analytes (tenant_id);
CREATE INDEX idx_catalog_analytes_tenant_created ON catalog_analytes (tenant_id, created_at);

COMMENT ON TABLE  catalog_analytes              IS 'Smallest measurable laboratory component (e.g. glucose, sodium).';
COMMENT ON COLUMN catalog_analytes.code         IS 'Short lab code, unique within tenant (e.g. GLU, NA, HGB).';
COMMENT ON COLUMN catalog_analytes.default_unit IS 'Default reporting unit (e.g. mg/dL, mmol/L). May be overridden per study.';
COMMENT ON COLUMN catalog_analytes.result_type  IS 'NUMERIC: numeric value with unit; TEXT: free text; QUALITATIVE: reactive/non-reactive.';
