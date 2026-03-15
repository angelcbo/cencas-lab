package com.cenicast.lis.catalog.repository;

import com.cenicast.lis.catalog.model.Technique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TechniqueRepository extends JpaRepository<Technique, UUID> {

    boolean existsByCodeAndTenantId(String code, UUID tenantId);

    @Query("SELECT t FROM Technique t WHERE t.id = :id")
    Optional<Technique> findByIdWithFilter(@Param("id") UUID id);
}
