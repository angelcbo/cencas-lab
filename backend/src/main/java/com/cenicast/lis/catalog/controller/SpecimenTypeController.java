package com.cenicast.lis.catalog.controller;

import com.cenicast.lis.catalog.dto.CreateSpecimenTypeRequest;
import com.cenicast.lis.catalog.dto.SpecimenTypeResponse;
import com.cenicast.lis.catalog.dto.UpdateSpecimenTypeRequest;
import com.cenicast.lis.catalog.service.SpecimenTypeService;
import com.cenicast.lis.common.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/specimen-types")
public class SpecimenTypeController {

    private final SpecimenTypeService specimenTypeService;

    public SpecimenTypeController(SpecimenTypeService specimenTypeService) {
        this.specimenTypeService = specimenTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LAB_ADMIN','LAB_ANALYST','LAB_RECEPTIONIST','LAB_DOCTOR')")
    public Page<SpecimenTypeResponse> listSpecimenTypes(Pageable pageable) {
        return specimenTypeService.listSpecimenTypes(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','LAB_ANALYST','LAB_RECEPTIONIST','LAB_DOCTOR')")
    public SpecimenTypeResponse getSpecimenType(@PathVariable UUID id) {
        return specimenTypeService.getSpecimenType(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public ResponseEntity<SpecimenTypeResponse> createSpecimenType(
            @Valid @RequestBody CreateSpecimenTypeRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        SpecimenTypeResponse created = specimenTypeService.createSpecimenType(
                req, principal.getTenantId(), principal, getClientIp(httpRequest));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public ResponseEntity<SpecimenTypeResponse> updateSpecimenType(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSpecimenTypeRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(specimenTypeService.updateSpecimenType(
                id, req, principal, getClientIp(httpRequest)));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
