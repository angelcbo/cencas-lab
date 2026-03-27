package com.cenicast.lis.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enriched response for get-by-id and create/update endpoints.
 * Analytes ordered by displayOrder ASC, then analyteCode ASC.
 * Techniques ordered by techniqueCode ASC.
 * Containers ordered by containerCode ASC.
 */
public record LabTestResponse(
    UUID id,
    UUID tenantId,
    String code,
    String name,
    UUID specimenTypeId,
    String specimenTypeName,
    int turnaroundTimeHours,
    BigDecimal price,
    boolean active,
    List<TestAnalyteDetail> analytes,
    List<TestTechniqueDetail> techniques,
    List<TestContainerDetail> containers,
    Instant createdAt,
    Instant updatedAt
) {
    public record TestAnalyteDetail(
        UUID id,
        UUID analyteId,
        String analyteCode,
        String analyteName,
        String defaultUnit,
        int displayOrder,
        boolean reportable
    ) {}

    public record TestTechniqueDetail(
        UUID id,
        UUID techniqueId,
        String techniqueCode,
        String techniqueName
    ) {}

    public record TestContainerDetail(
        UUID id,
        UUID collectionContainerId,
        String containerCode,
        String containerName,
        boolean required
    ) {}
}
