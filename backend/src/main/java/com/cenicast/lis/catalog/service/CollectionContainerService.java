package com.cenicast.lis.catalog.service;

import com.cenicast.lis.catalog.dto.CollectionContainerResponse;
import com.cenicast.lis.catalog.dto.CreateCollectionContainerRequest;
import com.cenicast.lis.catalog.dto.UpdateCollectionContainerRequest;
import com.cenicast.lis.catalog.model.CollectionContainer;
import com.cenicast.lis.catalog.model.SpecimenType;
import com.cenicast.lis.catalog.repository.CollectionContainerRepository;
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
public class CollectionContainerService {

    private final CollectionContainerRepository containerRepository;
    private final SpecimenTypeRepository specimenTypeRepository;
    private final AuditService auditService;

    public CollectionContainerService(CollectionContainerRepository containerRepository,
                                      SpecimenTypeRepository specimenTypeRepository,
                                      AuditService auditService) {
        this.containerRepository = containerRepository;
        this.specimenTypeRepository = specimenTypeRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<CollectionContainerResponse> listContainers(Pageable pageable) {
        return containerRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CollectionContainerResponse getContainer(UUID id) {
        return containerRepository.findByIdWithFilter(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Collection container not found"));
    }

    @Transactional
    public CollectionContainerResponse createContainer(CreateCollectionContainerRequest req,
                                                       UUID tenantId,
                                                       UserPrincipal actor,
                                                       String ipAddress) {
        if (containerRepository.existsByCodeAndTenantId(req.code(), tenantId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Collection container with code '" + req.code() + "' already exists");
        }
        // Validate specimenTypeId belongs to caller's tenant.
        // The Hibernate filter is active: findByIdWithFilter returns empty if the id
        // exists but belongs to a different tenant — resulting in a 422.
        SpecimenType specimenType = specimenTypeRepository.findByIdWithFilter(req.specimenTypeId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Specimen type not found"));

        CollectionContainer container = new CollectionContainer();
        container.setTenantId(tenantId);
        container.setCode(req.code());
        container.setName(req.name());
        container.setColor(req.color());
        container.setSpecimenTypeId(specimenType.getId());
        container.setDescription(req.description());
        container.setActive(true);
        container = containerRepository.save(container);
        auditService.recordResource(AuditAction.CREATE_COLLECTION_CONTAINER, actor,
                "CollectionContainer", container.getId(),
                Map.of("code", container.getCode()), ipAddress);
        return toResponse(container, specimenType.getName());
    }

    @Transactional
    public CollectionContainerResponse updateContainer(UUID id,
                                                       UpdateCollectionContainerRequest req,
                                                       UserPrincipal actor,
                                                       String ipAddress) {
        CollectionContainer container = containerRepository.findByIdWithFilter(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Collection container not found"));
        if (!container.getCode().equals(req.code())
                && containerRepository.existsByCodeAndTenantId(req.code(), container.getTenantId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Collection container with code '" + req.code() + "' already exists");
        }
        // Validate new specimenTypeId belongs to caller's tenant
        SpecimenType specimenType = specimenTypeRepository.findByIdWithFilter(req.specimenTypeId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Specimen type not found"));

        container.setCode(req.code());
        container.setName(req.name());
        container.setColor(req.color());
        container.setSpecimenTypeId(specimenType.getId());
        container.setDescription(req.description());
        container.setActive(req.active());
        container = containerRepository.save(container);
        auditService.recordResource(AuditAction.UPDATE_COLLECTION_CONTAINER, actor,
                "CollectionContainer", container.getId(),
                Map.of("code", container.getCode()), ipAddress);
        return toResponse(container, specimenType.getName());
    }

    private CollectionContainerResponse toResponse(CollectionContainer c) {
        String specimenTypeName = specimenTypeRepository.findByIdWithFilter(c.getSpecimenTypeId())
                .map(SpecimenType::getName)
                .orElse(null);
        return toResponse(c, specimenTypeName);
    }

    private CollectionContainerResponse toResponse(CollectionContainer c, String specimenTypeName) {
        return new CollectionContainerResponse(
                c.getId(), c.getTenantId(), c.getCode(), c.getName(), c.getColor(),
                c.getSpecimenTypeId(), specimenTypeName,
                c.getDescription(), c.isActive(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
