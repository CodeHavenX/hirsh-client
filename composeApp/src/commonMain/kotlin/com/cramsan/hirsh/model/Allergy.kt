package com.cramsan.hirsh.model

/**
 * Mirrors the backend's `AllergySummary` (HISS-621) -- one recorded allergy of a patient. The
 * dedicated CRUD UI for these (add/edit/remove) is HISS-623's job; this client currently only
 * displays them, derived into a single summary string on `PatientRecordScreen`.
 */
data class Allergy(
    val id: String,
    val allergyType: AllergyType,
    val description: String,
    val severity: Severity,
    val observations: String = "",
)

enum class AllergyType { MEDICATION, FOOD, ENVIRONMENTAL, OTHER }

enum class Severity { MILD, MODERATE, SEVERE }

/** Single free-text summary for display, matching the form-level representation until HISS-623. */
fun List<Allergy>.summary(): String = joinToString(", ") { it.description }.ifEmpty { "Ninguna" }

/**
 * Inverse of [summary] -- wraps a form's free-text allergies field into (at most) one synthetic
 * [Allergy] entry. `severity`/`allergyType` are placeholders with no real source until HISS-623
 * gives this a proper per-allergy form.
 */
fun singleAllergyFromText(text: String): List<Allergy> =
    if (text.isBlank() || text == "Ninguna") {
        emptyList()
    } else {
        listOf(Allergy(id = "allergy_$text", allergyType = AllergyType.OTHER, description = text, severity = Severity.MILD))
    }
