package com.cenicast.lis.catalog.dto;

import com.cenicast.lis.catalog.model.ResultType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Full replacement (PUT semantics). All non-optional fields are required.
 * defaultUnit may be null to clear it.
 */
public record UpdateAnalyteRequest(
        @NotBlank @Size(max = 50)  String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50)            String defaultUnit,
        @NotNull                   ResultType resultType,
        @NotNull                   Boolean active
) {}
