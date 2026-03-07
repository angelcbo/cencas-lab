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

    // Sprint 7+
    CREATE_INVOICE,
    RECORD_PAYMENT
}
