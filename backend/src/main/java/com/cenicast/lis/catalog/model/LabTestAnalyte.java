package com.cenicast.lis.catalog.model;

import com.cenicast.lis.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Relationship: maps a LabTest to one of its analytes, with display order and reportable flag.
 * Managed via replace-all — deleted and recreated on each update of the parent test.
 */
@Entity
@Table(name = "catalog_test_analytes")
public class LabTestAnalyte extends TenantAwareEntity {

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "analyte_id", nullable = false)
    private UUID analyteId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "reportable", nullable = false)
    private boolean reportable = true;

    public UUID getTestId()                       { return testId; }
    public void setTestId(UUID testId)            { this.testId = testId; }

    public UUID getAnalyteId()                    { return analyteId; }
    public void setAnalyteId(UUID analyteId)      { this.analyteId = analyteId; }

    public int getDisplayOrder()                  { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isReportable()                 { return reportable; }
    public void setReportable(boolean reportable) { this.reportable = reportable; }
}
