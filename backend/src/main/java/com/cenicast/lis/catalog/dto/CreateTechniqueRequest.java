package com.cenicast.lis.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTechniqueRequest(
        @NotBlank @Size(max = 50)  String code,
        @NotBlank @Size(max = 255) String name,
                                   String description
) {}
