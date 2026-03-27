package com.cenicast.lis.catalog.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LabTestAnalyteInput(
    @NotNull UUID analyteId,
    int displayOrder,
    boolean reportable
) {}
