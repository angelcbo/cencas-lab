package com.cenicast.lis.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * NOT tenant-scoped — token ownership follows the user, not the tenant filter.
 * No @Filter annotation.
 * userId is stored as a plain UUID (no @ManyToOne) to keep the auth module decoupled.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id")  // nullable — SUPER_ADMIN tokens have no tenant
    private UUID tenantId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private boolean revoked;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "replaced_by")  // nullable — set when token is rotated
    private UUID replacedBy;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID getId()                        { return id; }
    public void setId(UUID id)                 { this.id = id; }

    public String getTokenHash()               { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public UUID getUserId()                    { return userId; }
    public void setUserId(UUID userId)         { this.userId = userId; }

    public UUID getTenantId()                  { return tenantId; }
    public void setTenantId(UUID tenantId)     { this.tenantId = tenantId; }

    public Instant getExpiresAt()              { return expiresAt; }
    public void setExpiresAt(Instant expiresAt){ this.expiresAt = expiresAt; }

    public boolean isRevoked()                 { return revoked; }
    public void setRevoked(boolean revoked)    { this.revoked = revoked; }

    public UUID getFamilyId()                  { return familyId; }
    public void setFamilyId(UUID familyId)     { this.familyId = familyId; }

    public UUID getReplacedBy()                { return replacedBy; }
    public void setReplacedBy(UUID replacedBy) { this.replacedBy = replacedBy; }

    public String getIpAddress()               { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public Instant getCreatedAt()              { return createdAt; }
    public void setCreatedAt(Instant createdAt){ this.createdAt = createdAt; }
}
