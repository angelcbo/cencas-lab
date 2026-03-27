package com.cenicast.lis.catalog.repository;

import com.cenicast.lis.catalog.model.LabTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LabTestRepository extends JpaRepository<LabTest, UUID> {

    boolean existsByCodeAndTenantId(String code, UUID tenantId);

    /**
     * Must use JPQL so the Hibernate tenant filter applies.
     * JpaRepository.findById() calls EntityManager.find() which bypasses session filters.
     */
    @Query("SELECT t FROM LabTest t WHERE t.id = :id")
    Optional<LabTest> findByIdWithFilter(@Param("id") UUID id);

    /**
     * Search by code or name (case-insensitive). Respects the active Hibernate tenant filter.
     */
    @Query("SELECT t FROM LabTest t WHERE LOWER(t.code) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<LabTest> findByCodeOrNameContainingIgnoreCase(@Param("search") String search, Pageable pageable);
}
