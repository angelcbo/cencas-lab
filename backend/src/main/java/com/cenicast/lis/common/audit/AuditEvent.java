package com.cenicast.lis.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log entry. No @Filter — cross-cutting, not tenant-scoped.
 * No FK on actor_id — intentional, survives user deletion.
 * No @PreUpdate — rows must never be modified after insert.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "tenant_id")  // nullable — SUPER_ADMIN events have no tenant
    private UUID tenantId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String metadata;  // raw JSON string; null if no metadata

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID getId()                            { return id; }
    public void setId(UUID id)                     { this.id = id; }

    public UUID getTenantId()                      { return tenantId; }
    public void setTenantId(UUID tenantId)         { this.tenantId = tenantId; }

    public UUID getActorId()                       { return actorId; }
    public void setActorId(UUID actorId)           { this.actorId = actorId; }

    public String getActorEmail()                  { return actorEmail; }
    public void setActorEmail(String actorEmail)   { this.actorEmail = actorEmail; }

    public String getAction()                      { return action; }
    public void setAction(String action)           { this.action = action; }

    public String getResourceType()                        { return resourceType; }
    public void setResourceType(String resourceType)       { this.resourceType = resourceType; }

    public UUID getResourceId()                    { return resourceId; }
    public void setResourceId(UUID resourceId)     { this.resourceId = resourceId; }

    public String getMetadata()                    { return metadata; }
    public void setMetadata(String metadata)       { this.metadata = metadata; }

    public String getIpAddress()                   { return ipAddress; }
    public void setIpAddress(String ipAddress)     { this.ipAddress = ipAddress; }

    public String getCorrelationId()               { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public Instant getCreatedAt()                  { return createdAt; }
    public void setCreatedAt(Instant createdAt)    { this.createdAt = createdAt; }
}
