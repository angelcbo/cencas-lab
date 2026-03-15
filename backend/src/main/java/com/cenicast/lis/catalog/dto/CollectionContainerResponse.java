package com.cenicast.lis.catalog.dto;

import java.time.Instant;
import java.util.UUID;

public record CollectionContainerResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String color,
        UUID specimenTypeId,
        String specimenTypeName,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
