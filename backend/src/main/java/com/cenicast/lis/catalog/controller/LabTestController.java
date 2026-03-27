package com.cenicast.lis.catalog.controller;

import com.cenicast.lis.catalog.dto.CreateLabTestRequest;
import com.cenicast.lis.catalog.dto.LabTestResponse;
import com.cenicast.lis.catalog.dto.LabTestSummaryResponse;
import com.cenicast.lis.catalog.dto.UpdateLabTestRequest;
import com.cenicast.lis.catalog.service.LabTestService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/tests")
public class LabTestController {

    private final LabTestService labTestService;

    public LabTestController(LabTestService labTestService) {
        this.labTestService = labTestService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LAB_ADMIN','LAB_ANALYST','LAB_RECEPTIONIST','LAB_DOCTOR')")
    public Page<LabTestSummaryResponse> listLabTests(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return labTestService.listLabTests(search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','LAB_ANALYST','LAB_RECEPTIONIST','LAB_DOCTOR')")
    public LabTestResponse getLabTest(@PathVariable UUID id) {
        return labTestService.getLabTest(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public ResponseEntity<LabTestResponse> createLabTest(
            @Valid @RequestBody CreateLabTestRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        LabTestResponse created = labTestService.createLabTest(
                req, principal.getTenantId(), principal, getClientIp(httpRequest));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public ResponseEntity<LabTestResponse> updateLabTest(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLabTestRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(labTestService.updateLabTest(
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
