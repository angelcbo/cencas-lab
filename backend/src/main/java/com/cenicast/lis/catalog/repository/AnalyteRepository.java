package com.cenicast.lis.catalog.repository;

import com.cenicast.lis.catalog.model.Analyte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AnalyteRepository extends JpaRepository<Analyte, UUID> {

    boolean existsByCodeAndTenantId(String code, UUID tenantId);

    /**
     * JPQL lookup — Hibernate @Filter applies so results are automatically scoped
     * to the active tenant. Never use the inherited findById() (EntityManager.find
     * bypasses session filters).
     */
    @Query("SELECT a FROM Analyte a WHERE a.id = :id")
    Optional<Analyte> findByIdWithFilter(@Param("id") UUID id);
}
