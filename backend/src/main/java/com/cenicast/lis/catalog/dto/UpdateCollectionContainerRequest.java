package com.cenicast.lis.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * specimenTypeId is included for full PUT replacement but cannot change the FK
 * to a different tenant's specimen type — the service validates tenant ownership.
 */
public record UpdateCollectionContainerRequest(
        @NotBlank @Size(max = 50)  String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50)            String color,
        @NotNull                   UUID specimenTypeId,
                                   String description,
        @NotNull                   Boolean active
) {}
