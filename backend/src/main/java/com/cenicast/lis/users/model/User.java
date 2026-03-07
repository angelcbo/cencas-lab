package com.cenicast.lis.users.model;

import com.cenicast.lis.common.security.UserPrincipal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * NOT extending TenantAwareEntity because tenantId is nullable for SUPER_ADMIN.
 * @Filter is declared directly so the Hibernate tenant filter still applies for lab users.
 * SUPER_ADMIN rows have tenant_id IS NULL and are excluded by the filter when it is enabled.
 */
@Entity
@Table(name = "users")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class User {

    @Id
    private UUID id;

    @Column(name = "tenant_id")  // nullable — SUPER_ADMIN has no tenant
    private UUID tenantId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

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

    /** Static factory wiring UserPrincipal to User entity (completes Step 1 TODO). */
    public static UserPrincipal toPrincipal(User user) {
        return new UserPrincipal(
                user.id,
                user.tenantId,
                user.email,
                user.passwordHash,
                user.role.name(),
                user.active
        );
    }

    public UUID getId()                         { return id; }
    public void setId(UUID id)                  { this.id = id; }

    public UUID getTenantId()                   { return tenantId; }
    public void setTenantId(UUID tenantId)      { this.tenantId = tenantId; }

    public String getEmail()                    { return email; }
    public void setEmail(String email)          { this.email = email; }

    public String getPasswordHash()                      { return passwordHash; }
    public void setPasswordHash(String passwordHash)     { this.passwordHash = passwordHash; }

    public String getFirstName()                { return firstName; }
    public void setFirstName(String firstName)  { this.firstName = firstName; }

    public String getLastName()                 { return lastName; }
    public void setLastName(String lastName)    { this.lastName = lastName; }

    public Role getRole()                       { return role; }
    public void setRole(Role role)              { this.role = role; }

    public boolean isActive()                   { return active; }
    public void setActive(boolean active)       { this.active = active; }

    public Instant getCreatedAt()                     { return createdAt; }
    public void setCreatedAt(Instant createdAt)       { this.createdAt = createdAt; }

    public Instant getUpdatedAt()                     { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt)       { this.updatedAt = updatedAt; }
}
