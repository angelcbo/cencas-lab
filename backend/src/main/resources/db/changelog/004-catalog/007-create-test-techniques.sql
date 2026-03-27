-- catalog_test_techniques — links a test to the technique(s) used to run it.

CREATE TABLE catalog_test_techniques (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL,
    test_id      UUID        NOT NULL,
    technique_id UUID        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_catalog_test_techniques        PRIMARY KEY (id),
    CONSTRAINT fk_catalog_test_techniques_tenant FOREIGN KEY (tenant_id)    REFERENCES tenants(id),
    CONSTRAINT fk_catalog_test_techniques_test   FOREIGN KEY (test_id)      REFERENCES catalog_tests(id),
    CONSTRAINT fk_catalog_test_techniques_tech   FOREIGN KEY (technique_id) REFERENCES catalog_techniques(id),
    CONSTRAINT uq_catalog_test_techniques        UNIQUE (test_id, technique_id)
);

CREATE INDEX idx_catalog_test_techniques_test ON catalog_test_techniques (test_id);
CREATE INDEX idx_catalog_test_techniques_tech ON catalog_test_techniques (technique_id);

COMMENT ON TABLE catalog_test_techniques IS 'Maps a test to the laboratory technique(s) used to run it.';
