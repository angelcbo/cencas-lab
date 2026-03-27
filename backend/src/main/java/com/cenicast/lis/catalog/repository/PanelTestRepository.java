package com.cenicast.lis.catalog.repository;

import com.cenicast.lis.catalog.model.PanelTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PanelTestRepository extends JpaRepository<PanelTest, UUID> {

    @Query("SELECT pt FROM PanelTest pt WHERE pt.panelId = :panelId")
    List<PanelTest> findAllByPanelId(@Param("panelId") UUID panelId);

    @Query("SELECT COUNT(pt) FROM PanelTest pt WHERE pt.panelId = :panelId")
    long countByPanelId(@Param("panelId") UUID panelId);

    @Modifying
    @Query("DELETE FROM PanelTest pt WHERE pt.panelId = :panelId")
    void deleteAllByPanelId(@Param("panelId") UUID panelId);
}
