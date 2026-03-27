package com.cenicast.lis.catalog.repository;

import com.cenicast.lis.catalog.model.LabTestAnalyte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LabTestAnalyteRepository extends JpaRepository<LabTestAnalyte, UUID> {

    @Query("SELECT a FROM LabTestAnalyte a WHERE a.testId = :testId")
    List<LabTestAnalyte> findAllByTestId(@Param("testId") UUID testId);

    @Modifying
    @Query("DELETE FROM LabTestAnalyte a WHERE a.testId = :testId")
    void deleteAllByTestId(@Param("testId") UUID testId);
}
