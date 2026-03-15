-- Sprint 3 — catalog_collection_containers table
-- The physical vessel used to collect a specimen.
-- Examples: purple EDTA tube, red tube, 24 h urine container.
--
-- specimen_type_id is NOT NULL by design: a container without an associated specimen type
-- has no clinical meaning. Every container is defined for exactly one specimen type.

CREATE TABLE catalog_collection_containers (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    code             VARCHAR(50)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    color            VARCHAR(50),
    specimen_type_id UUID         NOT NULL,
    description      TEXT,
    active           BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_catalog_collection_containers        PRIMARY KEY (id),
    CONSTRAINT fk_catalog_collection_containers_tenant FOREIGN KEY (tenant_id)        REFERENCES tenants(id),
    CONSTRAINT fk_catalog_collection_containers_st     FOREIGN KEY (specimen_type_id) REFERENCES catalog_specimen_types(id),
    CONSTRAINT uq_catalog_collection_containers_code   UNIQUE (tenant_id, code)
);

CREATE INDEX idx_catalog_collection_containers_tenant         ON catalog_collection_containers (tenant_id);
CREATE INDEX idx_catalog_collection_containers_tenant_created ON catalog_collection_containers (tenant_id, created_at);
CREATE INDEX idx_catalog_collection_containers_st             ON catalog_collection_containers (specimen_type_id);

COMMENT ON TABLE  catalog_collection_containers                  IS 'Container used to collect a specimen (e.g. purple EDTA tube, urine cup).';
COMMENT ON COLUMN catalog_collection_containers.code             IS 'Short lab code, unique within tenant (e.g. EDTA-PURPLE, RED-TUBE).';
COMMENT ON COLUMN catalog_collection_containers.color            IS 'Tube cap color (e.g. "purple", "red"). Optional display hint for phlebotomists.';
COMMENT ON COLUMN catalog_collection_containers.specimen_type_id IS 'Every container is associated with exactly one specimen type. NOT NULL by design.';
