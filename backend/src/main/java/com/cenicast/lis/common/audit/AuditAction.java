package com.cenicast.lis.common.audit;

public enum AuditAction {
    // Auth events
    LOGIN,
    FAILED_LOGIN,
    LOGOUT,
    TOKEN_REFRESH,
    TOKEN_REVOKED,
    PASSWORD_CHANGE,

    // User management
    CREATE_USER,
    UPDATE_USER,
    DEACTIVATE_USER,

    // Tenant management
    CREATE_TENANT,
    UPDATE_TENANT,

    // Sprint 2+
    CREATE_PATIENT,
    UPDATE_PATIENT,

    // Sprint 4+
    CREATE_ORDER,
    UPDATE_ORDER_STATUS,

    // Sprint 5+
    ENTER_RESULT,
    VALIDATE_RESULT,

    // Sprint 3 — catalog management
    CREATE_ANALYTE, UPDATE_ANALYTE,
    CREATE_TECHNIQUE, UPDATE_TECHNIQUE,
    CREATE_SPECIMEN_TYPE, UPDATE_SPECIMEN_TYPE,
    CREATE_COLLECTION_CONTAINER, UPDATE_COLLECTION_CONTAINER,

    // Sprint 7+
    CREATE_INVOICE,
    RECORD_PAYMENT
}
