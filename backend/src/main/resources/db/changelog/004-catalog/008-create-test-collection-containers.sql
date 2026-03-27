-- catalog_test_collection_containers — links a test to the collection container(s) needed.

CREATE TABLE catalog_test_collection_containers (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID        NOT NULL,
    test_id                 UUID        NOT NULL,
    collection_container_id UUID        NOT NULL,
    required                BOOLEAN     NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_catalog_test_cc        PRIMARY KEY (id),
    CONSTRAINT fk_catalog_test_cc_tenant FOREIGN KEY (tenant_id)               REFERENCES tenants(id),
    CONSTRAINT fk_catalog_test_cc_test   FOREIGN KEY (test_id)                 REFERENCES catalog_tests(id),
    CONSTRAINT fk_catalog_test_cc_cc     FOREIGN KEY (collection_container_id) REFERENCES catalog_collection_containers(id),
    CONSTRAINT uq_catalog_test_cc        UNIQUE (test_id, collection_container_id)
);

CREATE INDEX idx_catalog_test_cc_test ON catalog_test_collection_containers (test_id);
CREATE INDEX idx_catalog_test_cc_cc   ON catalog_test_collection_containers (collection_container_id);

COMMENT ON TABLE  catalog_test_collection_containers          IS 'Maps a test to the collection container(s) needed to collect the specimen.';
COMMENT ON COLUMN catalog_test_collection_containers.required IS 'Whether this container is mandatory for specimen collection.';
