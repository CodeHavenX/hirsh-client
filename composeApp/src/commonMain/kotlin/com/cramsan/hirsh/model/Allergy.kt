package com.cramsan.hirsh.model

/**
 * Mirrors the backend's `AllergySummary` (HISS-621) -- one recorded allergy of a patient, its own
 * sub-resource (`/api/v1/patients/{patientId}/allergies`) rather than a field saved with the rest
 * of the patient. Per-allergy add/edit/remove lives on `EditPatientScreen` (HISS-623).
 *
 * [allergyType]/[description] are immutable once recorded -- the real `PatchAllergyRequest` only
 * accepts [severity]/[observations] (a wrong agent "is a different allergy, recorded as its own
 * entry"), so correcting one means removing it and adding a new one.
 */
data class Allergy(
    val id: String,
    val allergyType: AllergyType,
    val description: String,
    /** Null when not yet graded -- the backend's own semantics, never defaulted to a real grade. */
    val severity: Severity?,
    val observations: String = "",
)

enum class AllergyType { MEDICATION, FOOD, ENVIRONMENTAL, OTHER }

enum class Severity { MILD, MODERATE, SEVERE }

fun AllergyType.toDisplayLabel(): String = when (this) {
    AllergyType.MEDICATION -> "Medicamento"
    AllergyType.FOOD -> "Alimento"
    AllergyType.ENVIRONMENTAL -> "Ambiental"
    AllergyType.OTHER -> "Otro"
}

fun Severity?.toDisplayLabel(): String = when (this) {
    Severity.MILD -> "Leve"
    Severity.MODERATE -> "Moderada"
    Severity.SEVERE -> "Severa"
    null -> "Sin graduar"
}

/** Single free-text summary, used by the change-log diff and anywhere a one-line rendering fits. */
fun List<Allergy>.summary(): String = joinToString(", ") { it.description }.ifEmpty { "Ninguna" }
