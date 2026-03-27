package com.cenicast.lis.catalog.model;

import com.cenicast.lis.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A laboratory test definition that measures one or more analytes.
 * Tenant-scoped. Named LabTest to avoid collision with JUnit's Test annotation.
 */
@Entity
@Table(name = "catalog_tests")
public class LabTest extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "specimen_type_id", nullable = false)
    private UUID specimenTypeId;

    @Column(name = "turnaround_time_hours", nullable = false)
    private int turnaroundTimeHours;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getCode()                         { return code; }
    public void setCode(String code)                { this.code = code; }

    public String getName()                         { return name; }
    public void setName(String name)                { this.name = name; }

    public UUID getSpecimenTypeId()                 { return specimenTypeId; }
    public void setSpecimenTypeId(UUID id)          { this.specimenTypeId = id; }

    public int getTurnaroundTimeHours()             { return turnaroundTimeHours; }
    public void setTurnaroundTimeHours(int hours)   { this.turnaroundTimeHours = hours; }

    public BigDecimal getPrice()                    { return price; }
    public void setPrice(BigDecimal price)          { this.price = price; }

    public boolean isActive()                       { return active; }
    public void setActive(boolean active)           { this.active = active; }
}
