-- catalog_test_analytes — links a test to the analytes it measures.
-- One test can measure multiple analytes; each analyte has a display order and reportable flag.

CREATE TABLE catalog_test_analytes (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL,
    test_id       UUID        NOT NULL,
    analyte_id    UUID        NOT NULL,
    display_order INT         NOT NULL DEFAULT 0,
    reportable    BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_catalog_test_analytes        PRIMARY KEY (id),
    CONSTRAINT fk_catalog_test_analytes_tenant FOREIGN KEY (tenant_id)  REFERENCES tenants(id),
    CONSTRAINT fk_catalog_test_analytes_test   FOREIGN KEY (test_id)    REFERENCES catalog_tests(id),
    CONSTRAINT fk_catalog_test_analytes_analyte FOREIGN KEY (analyte_id) REFERENCES catalog_analytes(id),
    CONSTRAINT uq_catalog_test_analytes        UNIQUE (test_id, analyte_id),
    CONSTRAINT chk_catalog_test_analytes_order CHECK (display_order >= 0)
);

CREATE INDEX idx_catalog_test_analytes_test    ON catalog_test_analytes (test_id);
CREATE INDEX idx_catalog_test_analytes_analyte ON catalog_test_analytes (analyte_id);

COMMENT ON TABLE  catalog_test_analytes               IS 'Maps a test to the analytes it measures.';
COMMENT ON COLUMN catalog_test_analytes.display_order IS 'Order in which analyte results appear on the report, >= 0.';
COMMENT ON COLUMN catalog_test_analytes.reportable    IS 'Whether this analyte value is included in the patient-facing report.';
