package com.cenicast.lis.catalog.model;

import com.cenicast.lis.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Physical vessel used to collect a specimen (e.g. purple EDTA tube, urine cup).
 * Every container is associated with exactly one SpecimenType — NOT NULL by design.
 * Tenant-scoped.
 */
@Entity
@Table(name = "catalog_collection_containers")
public class CollectionContainer extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "specimen_type_id", nullable = false)
    private UUID specimenTypeId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getCode()                        { return code; }
    public void setCode(String code)               { this.code = code; }

    public String getName()                        { return name; }
    public void setName(String name)               { this.name = name; }

    public String getColor()                       { return color; }
    public void setColor(String color)             { this.color = color; }

    public UUID getSpecimenTypeId()                { return specimenTypeId; }
    public void setSpecimenTypeId(UUID id)         { this.specimenTypeId = id; }

    public String getDescription()                 { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive()                      { return active; }
    public void setActive(boolean active)          { this.active = active; }
}
