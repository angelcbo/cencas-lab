package com.cenicast.lis.tenant.service;

import com.cenicast.lis.common.audit.AuditAction;
import com.cenicast.lis.common.audit.AuditService;
import com.cenicast.lis.common.exception.ApiException;
import com.cenicast.lis.common.security.UserPrincipal;
import com.cenicast.lis.tenant.dto.TenantRequest;
import com.cenicast.lis.tenant.dto.TenantResponse;
import com.cenicast.lis.tenant.model.Tenant;
import com.cenicast.lis.tenant.repository.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    public TenantService(TenantRepository tenantRepository, AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<TenantResponse> listTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID id) {
        return tenantRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    @Transactional
    public TenantResponse createTenant(TenantRequest req, UserPrincipal actor, String ipAddress) {
        if (tenantRepository.existsBySlug(req.slug())) {
            throw new ApiException(HttpStatus.CONFLICT, "Tenant slug already exists: " + req.slug());
        }

        Tenant tenant = new Tenant();
        tenant.setSlug(req.slug());
        tenant.setName(req.name());
        tenant.setTimezone(req.timezone() != null ? req.timezone() : "America/Mexico_City");
        tenant.setTaxRate(req.taxRate() != null ? req.taxRate() : new java.math.BigDecimal("0.1600"));
        tenant.setActive(true);
        tenant = tenantRepository.save(tenant);

        auditService.recordResource(AuditAction.CREATE_TENANT, actor,
                "Tenant", tenant.getId(), Map.of("slug", tenant.getSlug()), ipAddress);

        return toResponse(tenant);
    }

    @Transactional
    public TenantResponse updateTenant(UUID id, TenantRequest req, UserPrincipal actor, String ipAddress) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tenant not found"));

        tenant.setName(req.name());
        if (req.timezone() != null) {
            tenant.setTimezone(req.timezone());
        }
        if (req.taxRate() != null) {
            tenant.setTaxRate(req.taxRate());
        }
        tenant = tenantRepository.save(tenant);

        auditService.recordResource(AuditAction.UPDATE_TENANT, actor,
                "Tenant", tenant.getId(), Map.of("slug", tenant.getSlug()), ipAddress);

        return toResponse(tenant);
    }

    @Transactional
    public void deactivateTenant(UUID id, UserPrincipal actor, String ipAddress) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenant.setActive(false);
        tenantRepository.save(tenant);

        auditService.recordResource(AuditAction.UPDATE_TENANT, actor,
                "Tenant", tenant.getId(), Map.of("slug", tenant.getSlug(), "action", "deactivated"), ipAddress);
    }

    private TenantResponse toResponse(Tenant t) {
        return new TenantResponse(t.getId(), t.getSlug(), t.getName(),
                t.getTimezone(), t.getTaxRate(), t.isActive(), t.getCreatedAt());
    }
}
