package com.cenicast.lis.catalog.model;

import com.cenicast.lis.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Type of biological material required for a test (e.g. whole blood, serum, plasma, urine).
 * Tenant-scoped.
 */
@Entity
@Table(name = "catalog_specimen_types")
public class SpecimenType extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getCode()                       { return code; }
    public void setCode(String code)              { this.code = code; }

    public String getName()                       { return name; }
    public void setName(String name)              { this.name = name; }

    public String getDescription()                { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive()                     { return active; }
    public void setActive(boolean active)         { this.active = active; }
}
