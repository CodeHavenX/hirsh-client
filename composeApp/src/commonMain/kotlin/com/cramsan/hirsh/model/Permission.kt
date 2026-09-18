package com.cramsan.hirsh.model

/**
 * Mirrors the backend's fixed permission-code catalog 1:1 (`security/Permissions.kt` in the
 * `hirsh` repo -- a separate repo, so this can't just reference it directly). Each entry's
 * [Enum.name] is exactly the code the API sends in `UserProfile`/`MeResponse.permissions` and
 * checks in [Session.can] against; keep both lists in sync if the backend's catalog changes.
 */
enum class Permission {
    // Users and RBAC
    USER_MANAGE,
    ROLE_MANAGE,

    // Catalogs and configuration
    CATALOG_MANAGE,
    CONFIG_MANAGE,

    // Patients
    PATIENT_READ,
    PATIENT_CREATE,
    PATIENT_WRITE,

    // Episodes and beds
    EPISODE_WRITE,
    EPISODE_DISCHARGE,

    // Documents
    TEMPLATE_MANAGE,
    DOCUMENT_READ,
    DOCUMENT_CREATE,
    DOCUMENT_SIGN,
    DOCUMENT_CANCEL,

    // Extracted clinical data
    TREATMENT_SUSPEND,

    // Tests
    TEST_MANAGE,
    TEST_READ,
    TEST_WRITE,

    // Interconsultations
    INTERCONSULT_READ,
    INTERCONSULT_WRITE,

    // Exports, audit, reports
    PDF_EXPORT,
    AUDIT_READ,
    REPORT_READ,
}
