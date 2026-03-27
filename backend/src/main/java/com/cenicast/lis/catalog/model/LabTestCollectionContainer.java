package com.cenicast.lis.catalog.model;

import com.cenicast.lis.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Relationship: maps a LabTest to a collection container needed for specimen collection.
 * Managed via replace-all — deleted and recreated on each update of the parent test.
 */
@Entity
@Table(name = "catalog_test_collection_containers")
public class LabTestCollectionContainer extends TenantAwareEntity {

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "collection_container_id", nullable = false)
    private UUID collectionContainerId;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    public UUID getTestId()                             { return testId; }
    public void setTestId(UUID testId)                  { this.testId = testId; }

    public UUID getCollectionContainerId()              { return collectionContainerId; }
    public void setCollectionContainerId(UUID id)       { this.collectionContainerId = id; }

    public boolean isRequired()                         { return required; }
    public void setRequired(boolean required)           { this.required = required; }
}
