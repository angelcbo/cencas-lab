package com.cenicast.lis.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateLabTestRequest(

    @NotBlank @Size(max = 50)
    String code,

    @NotBlank @Size(max = 255)
    String name,

    @NotNull
    UUID specimenTypeId,

    @NotNull @Min(1)
    Integer turnaroundTimeHours,

    @NotNull
    BigDecimal price,

    @NotNull
    List<@Valid LabTestAnalyteInput> analytes,

    @NotNull
    List<UUID> techniqueIds,

    @NotNull
    List<@Valid LabTestContainerInput> containers

) {}
