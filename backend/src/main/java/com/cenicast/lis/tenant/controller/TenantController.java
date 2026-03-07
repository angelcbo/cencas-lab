package com.cenicast.lis.tenant.controller;

import com.cenicast.lis.common.security.UserPrincipal;
import com.cenicast.lis.tenant.dto.TenantRequest;
import com.cenicast.lis.tenant.dto.TenantResponse;
import com.cenicast.lis.tenant.service.TenantService;
import com.cenicast.lis.users.dto.CreateUserRequest;
import com.cenicast.lis.users.dto.UserResponse;
import com.cenicast.lis.users.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantController {

    private final TenantService tenantService;
    private final UserService userService;

    public TenantController(TenantService tenantService, UserService userService) {
        this.tenantService = tenantService;
        this.userService = userService;
    }

    @GetMapping
    public Page<TenantResponse> listTenants(Pageable pageable) {
        return tenantService.listTenants(pageable);
    }

    @GetMapping("/{id}")
    public TenantResponse getTenant(@PathVariable UUID id) {
        return tenantService.getTenant(id);
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody TenantRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        TenantResponse created = tenantService.createTenant(req, principal, getClientIp(httpRequest));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public TenantResponse updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody TenantRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        return tenantService.updateTenant(id, req, principal, getClientIp(httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateTenant(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        tenantService.deactivateTenant(id, principal, getClientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * Create the first LAB_ADMIN for a tenant.
     * SUPER_ADMIN calls this endpoint (not POST /api/v1/users) to provision tenant users.
     * tenantId comes from the path variable — never from TenantContextHolder.
     */
    @PostMapping("/{tenantId}/users")
    public ResponseEntity<UserResponse> createTenantUser(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateUserRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        UserResponse created = userService.createUser(req, tenantId, principal, getClientIp(httpRequest));
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/v1/users/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
