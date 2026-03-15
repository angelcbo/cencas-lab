package com.cenicast.lis.catalog.repository;

import com.cenicast.lis.catalog.model.SpecimenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpecimenTypeRepository extends JpaRepository<SpecimenType, UUID> {

    boolean existsByCodeAndTenantId(String code, UUID tenantId);

    @Query("SELECT s FROM SpecimenType s WHERE s.id = :id")
    Optional<SpecimenType> findByIdWithFilter(@Param("id") UUID id);
}
