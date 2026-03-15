package com.cenicast.lis.catalog.dto;

import com.cenicast.lis.catalog.model.ResultType;

import java.time.Instant;
import java.util.UUID;

public record AnalyteResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String defaultUnit,
        ResultType resultType,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
