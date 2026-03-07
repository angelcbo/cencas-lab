package com.cenicast.lis.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Stateless principal rebuilt from JWT claims on every request — no DB lookup.
 * Static factory UserPrincipal.from(User) is added in Step 2 once the User entity exists.
 */
public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID tenantId;   // null for SUPER_ADMIN
    private final String email;
    private final String passwordHash;
    private final String role;
    private final boolean active;

    public UserPrincipal(UUID userId, UUID tenantId, String email,
                         String passwordHash, String role, boolean active) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    // Factory method lives on User.toPrincipal(User) to avoid circular dependency
    // (common.security cannot import users.model without creating a cycle).

    public UUID getUserId()   { return userId; }
    public UUID getTenantId() { return tenantId; }
    public String getEmail()  { return email; }
    public String getRole()   { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public String  getPassword()            { return passwordHash; }
    @Override public String  getUsername()             { return email; }
    @Override public boolean isEnabled()               { return active; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
