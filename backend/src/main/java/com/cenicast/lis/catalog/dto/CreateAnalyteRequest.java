package com.cenicast.lis.catalog.dto;

import com.cenicast.lis.catalog.model.ResultType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAnalyteRequest(
        @NotBlank @Size(max = 50)  String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50)            String defaultUnit,
        @NotNull                   ResultType resultType
) {}
