package com.cenicast.lis.catalog.repository;

import com.cenicast.lis.catalog.model.LabTestCollectionContainer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LabTestCollectionContainerRepository extends JpaRepository<LabTestCollectionContainer, UUID> {

    @Query("SELECT c FROM LabTestCollectionContainer c WHERE c.testId = :testId")
    List<LabTestCollectionContainer> findAllByTestId(@Param("testId") UUID testId);

    @Modifying
    @Query("DELETE FROM LabTestCollectionContainer c WHERE c.testId = :testId")
    void deleteAllByTestId(@Param("testId") UUID testId);
}
