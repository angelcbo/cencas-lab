package com.cenicast.lis.catalog.model;

import com.cenicast.lis.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * The smallest measurable laboratory component (e.g. glucose, sodium, hemoglobin).
 * Tenant-scoped: each lab owns its own analyte catalog.
 */
@Entity
@Table(name = "catalog_analytes")
public class Analyte extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "default_unit", length = 50)
    private String defaultUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 20)
    private ResultType resultType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getCode()                      { return code; }
    public void setCode(String code)             { this.code = code; }

    public String getName()                      { return name; }
    public void setName(String name)             { this.name = name; }

    public String getDefaultUnit()               { return defaultUnit; }
    public void setDefaultUnit(String defaultUnit) { this.defaultUnit = defaultUnit; }

    public ResultType getResultType()            { return resultType; }
    public void setResultType(ResultType rt)     { this.resultType = rt; }

    public boolean isActive()                    { return active; }
    public void setActive(boolean active)        { this.active = active; }
}
