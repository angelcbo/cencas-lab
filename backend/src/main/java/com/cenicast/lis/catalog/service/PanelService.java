package com.cenicast.lis.catalog.service;

import com.cenicast.lis.catalog.dto.CreatePanelRequest;
import com.cenicast.lis.catalog.dto.PanelTestInput;
import com.cenicast.lis.catalog.dto.PanelResponse;
import com.cenicast.lis.catalog.dto.PanelResponse.PanelTestDetail;
import com.cenicast.lis.catalog.dto.PanelSummaryResponse;
import com.cenicast.lis.catalog.dto.UpdatePanelRequest;
import com.cenicast.lis.catalog.model.LabTest;
import com.cenicast.lis.catalog.model.Panel;
import com.cenicast.lis.catalog.model.PanelTest;
import com.cenicast.lis.catalog.repository.LabTestRepository;
import com.cenicast.lis.catalog.repository.PanelRepository;
import com.cenicast.lis.catalog.repository.PanelTestRepository;
import com.cenicast.lis.common.audit.AuditAction;
import com.cenicast.lis.common.audit.AuditService;
import com.cenicast.lis.common.exception.ApiException;
import com.cenicast.lis.common.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PanelService {

    private final PanelRepository panelRepository;
    private final PanelTestRepository panelTestRepository;
    private final LabTestRepository labTestRepository;
    private final AuditService auditService;

    public PanelService(PanelRepository panelRepository,
                        PanelTestRepository panelTestRepository,
                        LabTestRepository labTestRepository,
                        AuditService auditService) {
        this.panelRepository = panelRepository;
        this.panelTestRepository = panelTestRepository;
        this.labTestRepository = labTestRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<PanelSummaryResponse> listPanels(String search, Pageable pageable) {
        Page<Panel> page = (search != null && !search.isBlank())
                ? panelRepository.findByCodeOrNameContainingIgnoreCase(search.trim(), pageable)
                : panelRepository.findAll(pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public PanelResponse getPanel(UUID id) {
        Panel panel = panelRepository.findByIdWithFilter(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Panel not found"));
        return toEnrichedResponse(panel);
    }

    @Transactional
    public PanelResponse createPanel(CreatePanelRequest req, UUID tenantId,
                                     UserPrincipal actor, String ipAddress) {
        // Step 1: code uniqueness
        if (panelRepository.existsByCodeAndTenantId(req.code(), tenantId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Panel with code '" + req.code() + "' already exists");
        }
        // Step 2: validate tests
        validateTests(req.tests());

        // All validations passed — persist
        Panel panel = new Panel();
        panel.setTenantId(tenantId);
        panel.setCode(req.code());
        panel.setName(req.name());
        panel.setDescription(req.description());
        panel.setActive(true);
        panel = panelRepository.save(panel);

        savePanelTests(panel.getId(), tenantId, req.tests());

        auditService.recordResource(AuditAction.CREATE_PANEL, actor,
                "Panel", panel.getId(), Map.of("code", panel.getCode()), ipAddress);
        return toEnrichedResponse(panel);
    }

    @Transactional
    public PanelResponse updatePanel(UUID id, UpdatePanelRequest req,
                                     UserPrincipal actor, String ipAddress) {
        // Step 1: load parent (404 if not found or cross-tenant)
        Panel panel = panelRepository.findByIdWithFilter(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Panel not found"));
        // Step 2: code uniqueness
        if (!panel.getCode().equals(req.code())
                && panelRepository.existsByCodeAndTenantId(req.code(), panel.getTenantId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Panel with code '" + req.code() + "' already exists");
        }
        // Step 3: validate tests
        validateTests(req.tests());

        // All validations passed — replace relationships then update entity
        panelTestRepository.deleteAllByPanelId(panel.getId());

        panel.setCode(req.code());
        panel.setName(req.name());
        panel.setDescription(req.description());
        panel.setActive(req.active());
        panel = panelRepository.save(panel);

        savePanelTests(panel.getId(), panel.getTenantId(), req.tests());

        auditService.recordResource(AuditAction.UPDATE_PANEL, actor,
                "Panel", panel.getId(), Map.of("code", panel.getCode()), ipAddress);
        return toEnrichedResponse(panel);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateTests(List<PanelTestInput> tests) {
        if (tests.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "At least one test is required for a panel");
        }
        Set<UUID> seen = new HashSet<>();
        for (PanelTestInput input : tests) {
            if (input.displayOrder() < 0) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Test displayOrder must be >= 0");
            }
            if (!seen.add(input.testId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Duplicate test entries in request");
            }
            LabTest labTest = labTestRepository.findByIdWithFilter(input.testId())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Lab test not found or not accessible"));
            if (!labTest.isActive()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Lab test '" + labTest.getCode() + "' is inactive");
            }
        }
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    private void savePanelTests(UUID panelId, UUID tenantId, List<PanelTestInput> tests) {
        for (PanelTestInput input : tests) {
            PanelTest link = new PanelTest();
            link.setTenantId(tenantId);
            link.setPanelId(panelId);
            link.setTestId(input.testId());
            link.setDisplayOrder(input.displayOrder());
            panelTestRepository.save(link);
        }
    }

    // ── Response mapping ──────────────────────────────────────────────────────

    private PanelSummaryResponse toSummary(Panel p) {
        long testCount = panelTestRepository.countByPanelId(p.getId());
        return new PanelSummaryResponse(
                p.getId(), p.getTenantId(), p.getCode(), p.getName(),
                p.getDescription(), p.isActive(), testCount,
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private PanelResponse toEnrichedResponse(Panel p) {
        List<com.cenicast.lis.catalog.model.PanelTest> links =
                panelTestRepository.findAllByPanelId(p.getId());

        List<PanelTestDetail> details = new ArrayList<>();
        for (com.cenicast.lis.catalog.model.PanelTest link : links) {
            labTestRepository.findByIdWithFilter(link.getTestId()).ifPresent(labTest ->
                details.add(new PanelTestDetail(
                        link.getId(), labTest.getId(),
                        labTest.getCode(), labTest.getName(),
                        link.getDisplayOrder()))
            );
        }
        // Sort: displayOrder ASC, then testCode ASC (stable tiebreaker)
        details.sort(Comparator.comparingInt(PanelTestDetail::displayOrder)
                .thenComparing(PanelTestDetail::testCode));

        return new PanelResponse(
                p.getId(), p.getTenantId(), p.getCode(), p.getName(),
                p.getDescription(), p.isActive(), details,
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
