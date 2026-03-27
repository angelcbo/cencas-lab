package com.cenicast.lis.catalog.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LabTestContainerInput(
    @NotNull UUID collectionContainerId,
    boolean required
) {}
