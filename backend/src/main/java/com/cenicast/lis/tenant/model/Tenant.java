package com.cenicast.lis.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * NOT tenant-scoped — this IS the tenant root table.
 * No @Filter annotation here.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false, length = 63)
    private String slug;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 50)
    private String timezone;

    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;

    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        id = UUID.randomUUID();
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId()                   { return id; }
    public void setId(UUID id)            { this.id = id; }

    public String getSlug()               { return slug; }
    public void setSlug(String slug)      { this.slug = slug; }

    public String getName()               { return name; }
    public void setName(String name)      { this.name = name; }

    public String getTimezone()                  { return timezone; }
    public void setTimezone(String timezone)     { this.timezone = timezone; }

    public BigDecimal getTaxRate()               { return taxRate; }
    public void setTaxRate(BigDecimal taxRate)   { this.taxRate = taxRate; }

    public boolean isActive()             { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt()                     { return createdAt; }
    public void setCreatedAt(Instant createdAt)       { this.createdAt = createdAt; }

    public Instant getUpdatedAt()                     { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt)       { this.updatedAt = updatedAt; }
}
