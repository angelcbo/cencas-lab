package com.cenicast.lis.catalog.dto;

import java.time.Instant;
import java.util.UUID;

public record TechniqueResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
