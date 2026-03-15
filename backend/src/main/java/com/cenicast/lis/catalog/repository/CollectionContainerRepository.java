package com.cenicast.lis.catalog.repository;

import com.cenicast.lis.catalog.model.CollectionContainer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CollectionContainerRepository extends JpaRepository<CollectionContainer, UUID> {

    boolean existsByCodeAndTenantId(String code, UUID tenantId);

    @Query("SELECT c FROM CollectionContainer c WHERE c.id = :id")
    Optional<CollectionContainer> findByIdWithFilter(@Param("id") UUID id);
}
