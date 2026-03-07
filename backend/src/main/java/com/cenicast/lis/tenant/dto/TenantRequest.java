package com.cenicast.lis.tenant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TenantRequest(
        @NotBlank @Size(max = 63) @Pattern(regexp = "[a-z0-9-]+", message = "Slug must contain only lowercase letters, digits, and hyphens")
        String slug,

        @NotBlank @Size(max = 255)
        String name,

        @Size(max = 50)
        String timezone,  // null → defaults to "America/Mexico_City" in service

        @DecimalMin("0.0000") @DecimalMax("1.0000")
        BigDecimal taxRate
) {}
