-- catalog_panel_tests — links a panel to its constituent tests with display order.

CREATE TABLE catalog_panel_tests (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL,
    panel_id      UUID        NOT NULL,
    test_id       UUID        NOT NULL,
    display_order INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_catalog_panel_tests        PRIMARY KEY (id),
    CONSTRAINT fk_catalog_panel_tests_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_catalog_panel_tests_panel  FOREIGN KEY (panel_id)  REFERENCES catalog_panels(id),
    CONSTRAINT fk_catalog_panel_tests_test   FOREIGN KEY (test_id)   REFERENCES catalog_tests(id),
    CONSTRAINT uq_catalog_panel_tests        UNIQUE (panel_id, test_id),
    CONSTRAINT chk_catalog_panel_tests_order CHECK (display_order >= 0)
);

CREATE INDEX idx_catalog_panel_tests_panel ON catalog_panel_tests (panel_id);
CREATE INDEX idx_catalog_panel_tests_test  ON catalog_panel_tests (test_id);

COMMENT ON TABLE  catalog_panel_tests               IS 'Maps a panel to the tests it contains.';
COMMENT ON COLUMN catalog_panel_tests.display_order IS 'Position of the test within the panel, >= 0.';
