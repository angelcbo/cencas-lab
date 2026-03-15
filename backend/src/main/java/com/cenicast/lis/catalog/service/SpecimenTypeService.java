package com.cenicast.lis.catalog.service;

import com.cenicast.lis.catalog.dto.CreateSpecimenTypeRequest;
import com.cenicast.lis.catalog.dto.SpecimenTypeResponse;
import com.cenicast.lis.catalog.dto.UpdateSpecimenTypeRequest;
import com.cenicast.lis.catalog.model.SpecimenType;
import com.cenicast.lis.catalog.repository.SpecimenTypeRepository;
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
public class SpecimenTypeService {

    private final SpecimenTypeRepository specimenTypeRepository;
    private final AuditService auditService;

    public SpecimenTypeService(SpecimenTypeRepository specimenTypeRepository, AuditService auditService) {
        this.specimenTypeRepository = specimenTypeRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<SpecimenTypeResponse> listSpecimenTypes(Pageable pageable) {
        return specimenTypeRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SpecimenTypeResponse getSpecimenType(UUID id) {
        return specimenTypeRepository.findByIdWithFilter(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specimen type not found"));
    }

    @Transactional
    public SpecimenTypeResponse createSpecimenType(CreateSpecimenTypeRequest req, UUID tenantId,
                                                   UserPrincipal actor, String ipAddress) {
        if (specimenTypeRepository.existsByCodeAndTenantId(req.code(), tenantId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Specimen type with code '" + req.code() + "' already exists");
        }
        SpecimenType specimenType = new SpecimenType();
        specimenType.setTenantId(tenantId);
        specimenType.setCode(req.code());
        specimenType.setName(req.name());
        specimenType.setDescription(req.description());
        specimenType.setActive(true);
        specimenType = specimenTypeRepository.save(specimenType);
        auditService.recordResource(AuditAction.CREATE_SPECIMEN_TYPE, actor,
                "SpecimenType", specimenType.getId(),
                Map.of("code", specimenType.getCode()), ipAddress);
        return toResponse(specimenType);
    }

    @Transactional
    public SpecimenTypeResponse updateSpecimenType(UUID id, UpdateSpecimenTypeRequest req,
                                                   UserPrincipal actor, String ipAddress) {
        SpecimenType specimenType = specimenTypeRepository.findByIdWithFilter(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specimen type not found"));
        if (!specimenType.getCode().equals(req.code())
                && specimenTypeRepository.existsByCodeAndTenantId(req.code(), specimenType.getTenantId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Specimen type with code '" + req.code() + "' already exists");
        }
        specimenType.setCode(req.code());
        specimenType.setName(req.name());
        specimenType.setDescription(req.description());
        specimenType.setActive(req.active());
        specimenType = specimenTypeRepository.save(specimenType);
        auditService.recordResource(AuditAction.UPDATE_SPECIMEN_TYPE, actor,
                "SpecimenType", specimenType.getId(),
                Map.of("code", specimenType.getCode()), ipAddress);
        return toResponse(specimenType);
    }

    private SpecimenTypeResponse toResponse(SpecimenType s) {
        return new SpecimenTypeResponse(
                s.getId(), s.getTenantId(), s.getCode(), s.getName(),
                s.getDescription(), s.isActive(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
