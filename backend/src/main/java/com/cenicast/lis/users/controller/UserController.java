package com.cenicast.lis.users.controller;

import com.cenicast.lis.common.security.UserPrincipal;
import com.cenicast.lis.users.dto.ChangePasswordRequest;
import com.cenicast.lis.users.dto.CreateUserRequest;
import com.cenicast.lis.users.dto.UpdateUserRequest;
import com.cenicast.lis.users.dto.UserResponse;
import com.cenicast.lis.users.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** LAB_ADMIN only — SUPER_ADMIN cannot list tenant users (see SUPER_ADMIN rules). */
    @GetMapping
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userService.listUsers(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'LAB_RECEPTIONIST')")
    public UserResponse getUser(@PathVariable UUID id) {
        return userService.getUser(id);
    }

    /**
     * Create a user in the caller's tenant.
     * tenantId from principal.getTenantId() — LAB_ADMIN only, not accessible by SUPER_ADMIN.
     * SUPER_ADMIN creates first LAB_ADMIN via POST /api/v1/tenants/{tenantId}/users instead.
     */
    @PostMapping
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        UserResponse created = userService.createUser(
                req, principal.getTenantId(), principal, getClientIp(httpRequest));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public UserResponse updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        return userService.updateUser(id, req, principal, getClientIp(httpRequest));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePasswordRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        userService.changePassword(id, req, principal, getClientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
