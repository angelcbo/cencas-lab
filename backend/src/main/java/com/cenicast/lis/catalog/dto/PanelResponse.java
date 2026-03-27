package com.cenicast.lis.catalog.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enriched response for get-by-id and create/update endpoints.
 * Tests ordered by displayOrder ASC, then testCode ASC.
 */
public record PanelResponse(
    UUID id,
    UUID tenantId,
    String code,
    String name,
    String description,
    boolean active,
    List<PanelTestDetail> tests,
    Instant createdAt,
    Instant updatedAt
) {
    public record PanelTestDetail(
        UUID id,
        UUID testId,
        String testCode,
        String testName,
        int displayOrder
    ) {}
}
