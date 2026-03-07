package com.cenicast.lis.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * tenantSlug == null → SUPER_ADMIN login path (no tenant lookup).
 * tenantSlug != null → lab user login; tenant is resolved from slug before user lookup.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        String tenantSlug
) {}
