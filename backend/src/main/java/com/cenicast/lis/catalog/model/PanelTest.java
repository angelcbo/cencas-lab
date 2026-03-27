package com.cenicast.lis.catalog.model;

import com.cenicast.lis.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Relationship: maps a Panel to one of its constituent LabTests with an explicit display order.
 * Managed via replace-all — deleted and recreated on each update of the parent panel.
 */
@Entity
@Table(name = "catalog_panel_tests")
public class PanelTest extends TenantAwareEntity {

    @Column(name = "panel_id", nullable = false)
    private UUID panelId;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    public UUID getPanelId()                      { return panelId; }
    public void setPanelId(UUID panelId)          { this.panelId = panelId; }

    public UUID getTestId()                       { return testId; }
    public void setTestId(UUID testId)            { this.testId = testId; }

    public int getDisplayOrder()                  { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
