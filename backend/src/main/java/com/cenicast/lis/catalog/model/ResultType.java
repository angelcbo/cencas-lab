package com.cenicast.lis.catalog.model;

/**
 * Determines how a result value is entered and validated.
 * Stored as VARCHAR(20) in the DB; CHECK constraint enforces valid values.
 */
public enum ResultType {
    /** Numeric value with an optional unit (e.g. 5.4 mg/dL). Auto-flagged against reference ranges. */
    NUMERIC,
    /** Free-text observation (e.g. morphology description). No auto-flagging. */
    TEXT,
    /** Enumerated outcome such as Reactive / Non-reactive / Indeterminate. */
    QUALITATIVE
}
