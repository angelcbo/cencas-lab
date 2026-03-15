package com.cenicast.lis.catalog.service;

import com.cenicast.lis.catalog.dto.AnalyteResponse;
import com.cenicast.lis.catalog.dto.CreateAnalyteRequest;
import com.cenicast.lis.catalog.dto.UpdateAnalyteRequest;
import com.cenicast.lis.catalog.model.Analyte;
import com.cenicast.lis.catalog.repository.AnalyteRepository;
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
public class AnalyteService {

    private final AnalyteRepository analyteRepository;
    private final AuditService auditService;

    public AnalyteService(AnalyteRepository analyteRepository, AuditService auditService) {
        this.analyteRepository = analyteRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<AnalyteResponse> listAnalytes(Pageable pageable) {
        return analyteRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AnalyteResponse getAnalyte(UUID id) {
        return analyteRepository.findByIdWithFilter(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analyte not found"));
    }

    @Transactional
    public AnalyteResponse createAnalyte(CreateAnalyteRequest req, UUID tenantId,
                                         UserPrincipal actor, String ipAddress) {
        if (analyteRepository.existsByCodeAndTenantId(req.code(), tenantId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Analyte with code '" + req.code() + "' already exists");
        }
        Analyte analyte = new Analyte();
        analyte.setTenantId(tenantId);
        analyte.setCode(req.code());
        analyte.setName(req.name());
        analyte.setDefaultUnit(req.defaultUnit());
        analyte.setResultType(req.resultType());
        analyte.setActive(true);
        analyte = analyteRepository.save(analyte);
        auditService.recordResource(AuditAction.CREATE_ANALYTE, actor,
                "Analyte", analyte.getId(), Map.of("code", analyte.getCode()), ipAddress);
        return toResponse(analyte);
    }

    @Transactional
    public AnalyteResponse updateAnalyte(UUID id, UpdateAnalyteRequest req,
                                         UserPrincipal actor, String ipAddress) {
        Analyte analyte = analyteRepository.findByIdWithFilter(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analyte not found"));
        if (!analyte.getCode().equals(req.code())
                && analyteRepository.existsByCodeAndTenantId(req.code(), analyte.getTenantId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Analyte with code '" + req.code() + "' already exists");
        }
        analyte.setCode(req.code());
        analyte.setName(req.name());
        analyte.setDefaultUnit(req.defaultUnit());
        analyte.setResultType(req.resultType());
        analyte.setActive(req.active());
        analyte = analyteRepository.save(analyte);
        auditService.recordResource(AuditAction.UPDATE_ANALYTE, actor,
                "Analyte", analyte.getId(), Map.of("code", analyte.getCode()), ipAddress);
        return toResponse(analyte);
    }

    private AnalyteResponse toResponse(Analyte a) {
        return new AnalyteResponse(
                a.getId(), a.getTenantId(), a.getCode(), a.getName(),
                a.getDefaultUnit(), a.getResultType(), a.isActive(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
