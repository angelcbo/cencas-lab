package com.cenicast.lis.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePanelRequest(

    @NotBlank @Size(max = 50)
    String code,

    @NotBlank @Size(max = 255)
    String name,

    String description,

    @NotNull
    Boolean active,

    @NotNull
    List<@Valid PanelTestInput> tests

) {}
