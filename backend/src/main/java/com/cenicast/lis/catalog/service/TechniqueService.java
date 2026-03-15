package com.cenicast.lis.catalog.service;

import com.cenicast.lis.catalog.dto.CreateTechniqueRequest;
import com.cenicast.lis.catalog.dto.TechniqueResponse;
import com.cenicast.lis.catalog.dto.UpdateTechniqueRequest;
import com.cenicast.lis.catalog.model.Technique;
import com.cenicast.lis.catalog.repository.TechniqueRepository;
import com.cenicast.lis.common.audit.AuditAction;
import com.cenicast.lis.common.audit.AuditService;
import com.cenicast.lis.common.exception.ApiException;
import com.cenicast.lis.common.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TechniqueService {

    private final TechniqueRepository techniqueRepository;
    private final AuditService auditService;

    public TechniqueService(TechniqueRepository techniqueRepository, AuditService auditService) {
        this.techniqueRepository = techniqueRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<TechniqueResponse> listTechniques(Pageable pageable) {
        return techniqueRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TechniqueResponse getTechnique(UUID id) {
        return techniqueRepository.findByIdWithFilter(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Technique not found"));
    }

    @Transactional
    public TechniqueResponse createTechnique(CreateTechniqueRequest req, UUID tenantId,
                                             UserPrincipal actor, String ipAddress) {
        if (techniqueRepository.existsByCodeAndTenantId(req.code(), tenantId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Technique with code '" + req.code() + "' already exists");
        }
        Technique technique = new Technique();
        technique.setTenantId(tenantId);
        technique.setCode(req.code());
        technique.setName(req.name());
        technique.setDescription(req.description());
        technique.setActive(true);
        technique = techniqueRepository.save(technique);
        auditService.recordResource(AuditAction.CREATE_TECHNIQUE, actor,
                "Technique", technique.getId(), Map.of("code", technique.getCode()), ipAddress);
        return toResponse(technique);
    }

    @Transactional
    public TechniqueResponse updateTechnique(UUID id, UpdateTechniqueRequest req,
                                             UserPrincipal actor, String ipAddress) {
        Technique technique = techniqueRepository.findByIdWithFilter(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Technique not found"));
        if (!technique.getCode().equals(req.code())
                && techniqueRepository.existsByCodeAndTenantId(req.code(), technique.getTenantId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Technique with code '" + req.code() + "' already exists");
        }
        technique.setCode(req.code());
        technique.setName(req.name());
        technique.setDescription(req.description());
        technique.setActive(req.active());
        technique = techniqueRepository.save(technique);
        auditService.recordResource(AuditAction.UPDATE_TECHNIQUE, actor,
                "Technique", technique.getId(), Map.of("code", technique.getCode()), ipAddress);
        return toResponse(technique);
    }

    private TechniqueResponse toResponse(Technique t) {
        return new TechniqueResponse(
                t.getId(), t.getTenantId(), t.getCode(), t.getName(),
                t.getDescription(), t.isActive(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
