package com.cenicast.lis.catalog.repository;

import com.cenicast.lis.catalog.model.LabTestTechnique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LabTestTechniqueRepository extends JpaRepository<LabTestTechnique, UUID> {

    @Query("SELECT t FROM LabTestTechnique t WHERE t.testId = :testId")
    List<LabTestTechnique> findAllByTestId(@Param("testId") UUID testId);

    @Modifying
    @Query("DELETE FROM LabTestTechnique t WHERE t.testId = :testId")
    void deleteAllByTestId(@Param("testId") UUID testId);
}
