-- Sprint 3/4 — catalog_tests table
-- A laboratory test definition that measures one or more analytes.
-- Tenant-scoped. Each test references exactly one specimen type.

CREATE TABLE catalog_tests (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID          NOT NULL,
    code                  VARCHAR(50)   NOT NULL,
    name                  VARCHAR(255)  NOT NULL,
    specimen_type_id      UUID          NOT NULL,
    turnaround_time_hours INT           NOT NULL,
    price                 NUMERIC(12,2) NOT NULL,
    active                BOOLEAN       NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_catalog_tests        PRIMARY KEY (id),
    CONSTRAINT fk_catalog_tests_tenant FOREIGN KEY (tenant_id)        REFERENCES tenants(id),
    CONSTRAINT fk_catalog_tests_st     FOREIGN KEY (specimen_type_id) REFERENCES catalog_specimen_types(id),
    CONSTRAINT uq_catalog_tests_code   UNIQUE (tenant_id, code),
    CONSTRAINT chk_catalog_tests_tat   CHECK (turnaround_time_hours > 0),
    CONSTRAINT chk_catalog_tests_price CHECK (price >= 0)
);

CREATE INDEX idx_catalog_tests_tenant         ON catalog_tests (tenant_id);
CREATE INDEX idx_catalog_tests_tenant_created ON catalog_tests (tenant_id, created_at);
CREATE INDEX idx_catalog_tests_st             ON catalog_tests (specimen_type_id);

COMMENT ON TABLE  catalog_tests                       IS 'Laboratory test definition. Groups analytes, techniques, and collection containers.';
COMMENT ON COLUMN catalog_tests.code                  IS 'Short lab code, unique within tenant (e.g. GLU, CBC, BMP).';
COMMENT ON COLUMN catalog_tests.specimen_type_id      IS 'The specimen type required to run this test.';
COMMENT ON COLUMN catalog_tests.turnaround_time_hours IS 'Expected turnaround time in hours, must be > 0.';
COMMENT ON COLUMN catalog_tests.price                 IS 'Base price for the test, must be >= 0.';
