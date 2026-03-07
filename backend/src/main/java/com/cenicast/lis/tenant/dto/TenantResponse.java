package com.cenicast.lis.tenant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String slug,
        String name,
        String timezone,
        BigDecimal taxRate,
        boolean active,
        Instant createdAt
) {}
