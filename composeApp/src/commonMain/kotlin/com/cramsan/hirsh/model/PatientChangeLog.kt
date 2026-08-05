package com.cramsan.hirsh.model

/**
 * One save action on a patient's editable fields, grouping every field
 * changed in that save under a single actor/timestamp -- mirrors
 * prototype/shared/data.js's logPatientChange() entry shape.
 */
data class PatientChangeLogEntry(
    val changedBy: String,
    val fecha: String,
    val hora: String,
    val fields: List<FieldChange>,
)

/**
 * [field] is the Kotlin Patient property name (e.g. "bloodType"), not the
 * prototype's raw PATIENT_FIELDS key (e.g. "blood") -- kept consistent with
 * Patient.kt's own naming rather than carrying two different vocabularies
 * for the same data into one log entry.
 */
data class FieldChange(
    val field: String,
    val label: String,
    val oldValue: String,
    val newValue: String,
)
