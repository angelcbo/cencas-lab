package com.cenicast.lis.catalog.controller;

import com.cenicast.lis.catalog.dto.CreatePanelRequest;
import com.cenicast.lis.catalog.dto.PanelResponse;
import com.cenicast.lis.catalog.dto.PanelSummaryResponse;
import com.cenicast.lis.catalog.dto.UpdatePanelRequest;
import com.cenicast.lis.catalog.service.PanelService;
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
@RequestMapping("/api/v1/catalog/panels")
public class PanelController {

    private final PanelService panelService;

    public PanelController(PanelService panelService) {
        this.panelService = panelService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LAB_ADMIN','LAB_ANALYST','LAB_RECEPTIONIST','LAB_DOCTOR')")
    public Page<PanelSummaryResponse> listPanels(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return panelService.listPanels(search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','LAB_ANALYST','LAB_RECEPTIONIST','LAB_DOCTOR')")
    public PanelResponse getPanel(@PathVariable UUID id) {
        return panelService.getPanel(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public ResponseEntity<PanelResponse> createPanel(
            @Valid @RequestBody CreatePanelRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        PanelResponse created = panelService.createPanel(
                req, principal.getTenantId(), principal, getClientIp(httpRequest));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public ResponseEntity<PanelResponse> updatePanel(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePanelRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(panelService.updatePanel(
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
