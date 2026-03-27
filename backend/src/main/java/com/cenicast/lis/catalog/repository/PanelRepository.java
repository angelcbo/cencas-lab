package com.cenicast.lis.catalog.repository;

import com.cenicast.lis.catalog.model.Panel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PanelRepository extends JpaRepository<Panel, UUID> {

    boolean existsByCodeAndTenantId(String code, UUID tenantId);

    @Query("SELECT p FROM Panel p WHERE p.id = :id")
    Optional<Panel> findByIdWithFilter(@Param("id") UUID id);

    @Query("SELECT p FROM Panel p WHERE LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Panel> findByCodeOrNameContainingIgnoreCase(@Param("search") String search, Pageable pageable);
}
