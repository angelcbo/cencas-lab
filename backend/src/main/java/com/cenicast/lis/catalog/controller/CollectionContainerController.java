package com.cenicast.lis.catalog.controller;

import com.cenicast.lis.catalog.dto.CollectionContainerResponse;
import com.cenicast.lis.catalog.dto.CreateCollectionContainerRequest;
import com.cenicast.lis.catalog.dto.UpdateCollectionContainerRequest;
import com.cenicast.lis.catalog.service.CollectionContainerService;
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
@RequestMapping("/api/v1/catalog/collection-containers")
public class CollectionContainerController {

    private final CollectionContainerService containerService;

    public CollectionContainerController(CollectionContainerService containerService) {
        this.containerService = containerService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LAB_ADMIN','LAB_ANALYST','LAB_RECEPTIONIST','LAB_DOCTOR')")
    public Page<CollectionContainerResponse> listContainers(Pageable pageable) {
        return containerService.listContainers(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LAB_ADMIN','LAB_ANALYST','LAB_RECEPTIONIST','LAB_DOCTOR')")
    public CollectionContainerResponse getContainer(@PathVariable UUID id) {
        return containerService.getContainer(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public ResponseEntity<CollectionContainerResponse> createContainer(
            @Valid @RequestBody CreateCollectionContainerRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        CollectionContainerResponse created = containerService.createContainer(
                req, principal.getTenantId(), principal, getClientIp(httpRequest));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ADMIN')")
    public ResponseEntity<CollectionContainerResponse> updateContainer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCollectionContainerRequest req,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(containerService.updateContainer(
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
