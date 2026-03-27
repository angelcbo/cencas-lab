package com.cenicast.lis.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight response for list endpoints — no composition details.
 */
public record LabTestSummaryResponse(
    UUID id,
    UUID tenantId,
    String code,
    String name,
    UUID specimenTypeId,
    String specimenTypeName,
    int turnaroundTimeHours,
    BigDecimal price,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
