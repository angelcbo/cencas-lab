package com.cenicast.lis.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCollectionContainerRequest(
        @NotBlank @Size(max = 50)  String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50)            String color,
        @NotNull                   UUID specimenTypeId,
                                   String description
) {}
