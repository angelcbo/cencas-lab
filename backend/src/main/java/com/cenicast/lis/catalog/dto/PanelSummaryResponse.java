package com.cenicast.lis.catalog.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight response for list endpoints — no test composition details.
 */
public record PanelSummaryResponse(
    UUID id,
    UUID tenantId,
    String code,
    String name,
    String description,
    boolean active,
    long testCount,
    Instant createdAt,
    Instant updatedAt
) {}
