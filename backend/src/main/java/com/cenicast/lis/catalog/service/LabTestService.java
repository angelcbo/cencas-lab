package com.cenicast.lis.catalog.service;

import com.cenicast.lis.catalog.dto.CreateLabTestRequest;
import com.cenicast.lis.catalog.dto.LabTestAnalyteInput;
import com.cenicast.lis.catalog.dto.LabTestContainerInput;
import com.cenicast.lis.catalog.dto.LabTestResponse;
import com.cenicast.lis.catalog.dto.LabTestResponse.TestAnalyteDetail;
import com.cenicast.lis.catalog.dto.LabTestResponse.TestContainerDetail;
import com.cenicast.lis.catalog.dto.LabTestResponse.TestTechniqueDetail;
import com.cenicast.lis.catalog.dto.LabTestSummaryResponse;
import com.cenicast.lis.catalog.dto.UpdateLabTestRequest;
import com.cenicast.lis.catalog.model.Analyte;
import com.cenicast.lis.catalog.model.CollectionContainer;
import com.cenicast.lis.catalog.model.LabTest;
import com.cenicast.lis.catalog.model.LabTestAnalyte;
import com.cenicast.lis.catalog.model.LabTestCollectionContainer;
import com.cenicast.lis.catalog.model.LabTestTechnique;
import com.cenicast.lis.catalog.model.SpecimenType;
import com.cenicast.lis.catalog.model.Technique;
import com.cenicast.lis.catalog.repository.AnalyteRepository;
import com.cenicast.lis.catalog.repository.CollectionContainerRepository;
import com.cenicast.lis.catalog.repository.LabTestAnalyteRepository;
import com.cenicast.lis.catalog.repository.LabTestCollectionContainerRepository;
import com.cenicast.lis.catalog.repository.LabTestRepository;
import com.cenicast.lis.catalog.repository.LabTestTechniqueRepository;
import com.cenicast.lis.catalog.repository.SpecimenTypeRepository;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LabTestService {

    private final LabTestRepository labTestRepository;
    private final LabTestAnalyteRepository labTestAnalyteRepository;
    private final LabTestTechniqueRepository labTestTechniqueRepository;
    private final LabTestCollectionContainerRepository labTestContainerRepository;
    private final AnalyteRepository analyteRepository;
    private final TechniqueRepository techniqueRepository;
    private final SpecimenTypeRepository specimenTypeRepository;
    private final CollectionContainerRepository collectionContainerRepository;
    private final AuditService auditService;

    public LabTestService(LabTestRepository labTestRepository,
                          LabTestAnalyteRepository labTestAnalyteRepository,
                          LabTestTechniqueRepository labTestTechniqueRepository,
                          LabTestCollectionContainerRepository labTestContainerRepository,
                          AnalyteRepository analyteRepository,
                          TechniqueRepository techniqueRepository,
                          SpecimenTypeRepository specimenTypeRepository,
                          CollectionContainerRepository collectionContainerRepository,
                          AuditService auditService) {
        this.labTestRepository = labTestRepository;
        this.labTestAnalyteRepository = labTestAnalyteRepository;
        this.labTestTechniqueRepository = labTestTechniqueRepository;
        this.labTestContainerRepository = labTestContainerRepository;
        this.analyteRepository = analyteRepository;
        this.techniqueRepository = techniqueRepository;
        this.specimenTypeRepository = specimenTypeRepository;
        this.collectionContainerRepository = collectionContainerRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<LabTestSummaryResponse> listLabTests(String search, Pageable pageable) {
        Page<LabTest> page = (search != null && !search.isBlank())
                ? labTestRepository.findByCodeOrNameContainingIgnoreCase(search.trim(), pageable)
                : labTestRepository.findAll(pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public LabTestResponse getLabTest(UUID id) {
        LabTest test = labTestRepository.findByIdWithFilter(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lab test not found"));
        return toEnrichedResponse(test);
    }

    @Transactional
    public LabTestResponse createLabTest(CreateLabTestRequest req, UUID tenantId,
                                         UserPrincipal actor, String ipAddress) {
        // Step 1: code uniqueness
        if (labTestRepository.existsByCodeAndTenantId(req.code(), tenantId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Lab test with code '" + req.code() + "' already exists");
        }
        // Step 2: validate price
        if (req.price().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Price must be >= 0");
        }
        // Step 3: validate specimen type
        SpecimenType specimenType = requireActiveSpecimenType(req.specimenTypeId());
        // Steps 4-6: validate composition
        validateComposition(req.analytes(), req.techniqueIds(), req.containers());

        // All validations passed — persist
        LabTest test = new LabTest();
        test.setTenantId(tenantId);
        test.setCode(req.code());
        test.setName(req.name());
        test.setSpecimenTypeId(specimenType.getId());
        test.setTurnaroundTimeHours(req.turnaroundTimeHours());
        test.setPrice(req.price());
        test.setActive(true);
        test = labTestRepository.save(test);

        saveComposition(test.getId(), tenantId, req.analytes(), req.techniqueIds(), req.containers());

        auditService.recordResource(AuditAction.CREATE_LAB_TEST, actor,
                "LabTest", test.getId(), Map.of("code", test.getCode()), ipAddress);
        return toEnrichedResponse(test);
    }

    @Transactional
    public LabTestResponse updateLabTest(UUID id, UpdateLabTestRequest req,
                                         UserPrincipal actor, String ipAddress) {
        // Step 1: load parent (404 if not found or cross-tenant)
        LabTest test = labTestRepository.findByIdWithFilter(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lab test not found"));
        // Step 2: code uniqueness
        if (!test.getCode().equals(req.code())
                && labTestRepository.existsByCodeAndTenantId(req.code(), test.getTenantId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Lab test with code '" + req.code() + "' already exists");
        }
        // Step 3: validate price
        if (req.price().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Price must be >= 0");
        }
        // Step 4: validate specimen type
        SpecimenType specimenType = requireActiveSpecimenType(req.specimenTypeId());
        // Steps 5-7: validate composition
        validateComposition(req.analytes(), req.techniqueIds(), req.containers());

        // All validations passed — replace relationships then update entity
        labTestAnalyteRepository.deleteAllByTestId(test.getId());
        labTestTechniqueRepository.deleteAllByTestId(test.getId());
        labTestContainerRepository.deleteAllByTestId(test.getId());

        test.setCode(req.code());
        test.setName(req.name());
        test.setSpecimenTypeId(specimenType.getId());
        test.setTurnaroundTimeHours(req.turnaroundTimeHours());
        test.setPrice(req.price());
        test.setActive(req.active());
        test = labTestRepository.save(test);

        saveComposition(test.getId(), test.getTenantId(), req.analytes(), req.techniqueIds(), req.containers());

        auditService.recordResource(AuditAction.UPDATE_LAB_TEST, actor,
                "LabTest", test.getId(), Map.of("code", test.getCode()), ipAddress);
        return toEnrichedResponse(test);
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private SpecimenType requireActiveSpecimenType(UUID id) {
        SpecimenType st = specimenTypeRepository.findByIdWithFilter(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Specimen type not found or not accessible"));
        if (!st.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Specimen type '" + st.getCode() + "' is inactive");
        }
        return st;
    }

    private void validateComposition(List<LabTestAnalyteInput> analytes,
                                     List<UUID> techniqueIds,
                                     List<LabTestContainerInput> containers) {
        if (analytes.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "At least one analyte is required");
        }
        if (containers.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "At least one collection container is required");
        }
        validateAnalytes(analytes);
        validateTechniques(techniqueIds);
        validateContainers(containers);
    }

    private void validateAnalytes(List<LabTestAnalyteInput> analytes) {
        Set<UUID> seen = new HashSet<>();
        for (LabTestAnalyteInput input : analytes) {
            if (input.displayOrder() < 0) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Analyte displayOrder must be >= 0");
            }
            if (!seen.add(input.analyteId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Duplicate analyte entries in request");
            }
            Analyte analyte = analyteRepository.findByIdWithFilter(input.analyteId())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Analyte not found or not accessible"));
            if (!analyte.isActive()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Analyte '" + analyte.getCode() + "' is inactive");
            }
        }
    }

    private void validateTechniques(List<UUID> techniqueIds) {
        Set<UUID> seen = new HashSet<>();
        for (UUID tid : techniqueIds) {
            if (!seen.add(tid)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Duplicate technique entries in request");
            }
            Technique technique = techniqueRepository.findByIdWithFilter(tid)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Technique not found or not accessible"));
            if (!technique.isActive()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Technique '" + technique.getCode() + "' is inactive");
            }
        }
    }

    private void validateContainers(List<LabTestContainerInput> containers) {
        Set<UUID> seen = new HashSet<>();
        for (LabTestContainerInput input : containers) {
            if (!seen.add(input.collectionContainerId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Duplicate collection container entries in request");
            }
            CollectionContainer container = collectionContainerRepository
                    .findByIdWithFilter(input.collectionContainerId())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Collection container not found or not accessible"));
            if (!container.isActive()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Collection container '" + container.getCode() + "' is inactive");
            }
        }
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    private void saveComposition(UUID testId, UUID tenantId,
                                 List<LabTestAnalyteInput> analytes,
                                 List<UUID> techniqueIds,
                                 List<LabTestContainerInput> containers) {
        for (LabTestAnalyteInput input : analytes) {
            LabTestAnalyte link = new LabTestAnalyte();
            link.setTenantId(tenantId);
            link.setTestId(testId);
            link.setAnalyteId(input.analyteId());
            link.setDisplayOrder(input.displayOrder());
            link.setReportable(input.reportable());
            labTestAnalyteRepository.save(link);
        }
        for (UUID tid : techniqueIds) {
            LabTestTechnique link = new LabTestTechnique();
            link.setTenantId(tenantId);
            link.setTestId(testId);
            link.setTechniqueId(tid);
            labTestTechniqueRepository.save(link);
        }
        for (LabTestContainerInput input : containers) {
            LabTestCollectionContainer link = new LabTestCollectionContainer();
            link.setTenantId(tenantId);
            link.setTestId(testId);
            link.setCollectionContainerId(input.collectionContainerId());
            link.setRequired(input.required());
            labTestContainerRepository.save(link);
        }
    }

    // ── Response mapping ──────────────────────────────────────────────────────

    private LabTestSummaryResponse toSummary(LabTest t) {
        String specimenTypeName = specimenTypeRepository.findByIdWithFilter(t.getSpecimenTypeId())
                .map(SpecimenType::getName)
                .orElse(null);
        return new LabTestSummaryResponse(
                t.getId(), t.getTenantId(), t.getCode(), t.getName(),
                t.getSpecimenTypeId(), specimenTypeName,
                t.getTurnaroundTimeHours(), t.getPrice(), t.isActive(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private LabTestResponse toEnrichedResponse(LabTest t) {
        String specimenTypeName = specimenTypeRepository.findByIdWithFilter(t.getSpecimenTypeId())
                .map(SpecimenType::getName)
                .orElse(null);

        List<TestAnalyteDetail> analyteDetails = buildAnalyteDetails(t.getId());
        List<TestTechniqueDetail> techniqueDetails = buildTechniqueDetails(t.getId());
        List<TestContainerDetail> containerDetails = buildContainerDetails(t.getId());

        return new LabTestResponse(
                t.getId(), t.getTenantId(), t.getCode(), t.getName(),
                t.getSpecimenTypeId(), specimenTypeName,
                t.getTurnaroundTimeHours(), t.getPrice(), t.isActive(),
                analyteDetails, techniqueDetails, containerDetails,
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private List<TestAnalyteDetail> buildAnalyteDetails(UUID testId) {
        List<LabTestAnalyte> links = labTestAnalyteRepository.findAllByTestId(testId);
        List<TestAnalyteDetail> details = new ArrayList<>();
        for (LabTestAnalyte link : links) {
            analyteRepository.findByIdWithFilter(link.getAnalyteId()).ifPresent(analyte ->
                details.add(new TestAnalyteDetail(
                        link.getId(), analyte.getId(),
                        analyte.getCode(), analyte.getName(), analyte.getDefaultUnit(),
                        link.getDisplayOrder(), link.isReportable()))
            );
        }
        // Sort: displayOrder ASC, then analyteCode ASC (stable tiebreaker)
        details.sort(Comparator.comparingInt(TestAnalyteDetail::displayOrder)
                .thenComparing(TestAnalyteDetail::analyteCode));
        return details;
    }

    private List<TestTechniqueDetail> buildTechniqueDetails(UUID testId) {
        List<LabTestTechnique> links = labTestTechniqueRepository.findAllByTestId(testId);
        List<TestTechniqueDetail> details = new ArrayList<>();
        for (LabTestTechnique link : links) {
            techniqueRepository.findByIdWithFilter(link.getTechniqueId()).ifPresent(technique ->
                details.add(new TestTechniqueDetail(
                        link.getId(), technique.getId(),
                        technique.getCode(), technique.getName()))
            );
        }
        // Sort: techniqueCode ASC (deterministic)
        details.sort(Comparator.comparing(TestTechniqueDetail::techniqueCode));
        return details;
    }

    private List<TestContainerDetail> buildContainerDetails(UUID testId) {
        List<LabTestCollectionContainer> links = labTestContainerRepository.findAllByTestId(testId);
        List<TestContainerDetail> details = new ArrayList<>();
        for (LabTestCollectionContainer link : links) {
            collectionContainerRepository.findByIdWithFilter(link.getCollectionContainerId())
                    .ifPresent(container ->
                        details.add(new TestContainerDetail(
                                link.getId(), container.getId(),
                                container.getCode(), container.getName(),
                                link.isRequired()))
                    );
        }
        // Sort: containerCode ASC (deterministic)
        details.sort(Comparator.comparing(TestContainerDetail::containerCode));
        return details;
    }
}
