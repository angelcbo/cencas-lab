package com.cenicast.lis.catalog.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PanelTestInput(
    @NotNull UUID testId,
    int displayOrder
) {}
