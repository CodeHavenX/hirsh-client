package com.cramsan.hirsh.model

import kotlinx.datetime.LocalDate

/** How a drug is given; the names are the backend's `RouteOfAdministration`. */
enum class RouteOfAdministration { PO, IM, IV, SC, SL, TOPICAL, INHALED }

fun RouteOfAdministration.toDisplayLabel(): String = when (this) {
    RouteOfAdministration.PO -> "Oral (VO)"
    RouteOfAdministration.IM -> "Intramuscular (IM)"
    RouteOfAdministration.IV -> "Endovenosa (EV)"
    RouteOfAdministration.SC -> "Subcutanea (SC)"
    RouteOfAdministration.SL -> "Sublingual (SL)"
    RouteOfAdministration.TOPICAL -> "Topica"
    RouteOfAdministration.INHALED -> "Inhalatoria"
}

/** The short form clinicians write on a prescription line. */
fun RouteOfAdministration.toShortLabel(): String = when (this) {
    RouteOfAdministration.PO -> "VO"
    RouteOfAdministration.IV -> "EV"
    RouteOfAdministration.TOPICAL -> "Topica"
    RouteOfAdministration.INHALED -> "Inhalatoria"
    else -> name
}

/** A formulary entry, as the drug picker shows it. */
data class Drug(
    val id: String,
    val genericName: String,
    val concentration: String?,
    val pharmaceuticalForm: String?,
    val route: RouteOfAdministration?,
    val therapeuticGroup: String?,
    val isPsychotropic: Boolean,
) {
    /** "Haloperidol 5 mg/mL · Ampolla": the formulary can list one drug in several presentations. */
    val displayName: String
        get() = listOfNotNull(
            listOfNotNull(genericName, concentration).joinToString(" "),
            pharmaceuticalForm,
        ).joinToString(" · ")
}

/**
 * One drug prescribed in an evolucion. Signing the evolucion turns each one into a treatment on the
 * backend (see [ActiveTreatment]); [drugName] is what that treatment shows.
 */
data class Prescripcion(
    val drugId: String?,
    val drugName: String,
    val dose: String,
    val frequency: String,
    val route: RouteOfAdministration?,
    val duration: String,
    val instructions: String,
)

/** "Sertralina 50 mg · Tableta — 50 mg — Cada 24 h — VO — 30 dias". */
fun Prescripcion.toDisplayLine(): String =
    listOf(drugName, dose, frequency, route?.toShortLabel().orEmpty(), duration, instructions)
        .filter { it.isNotBlank() }
        .joinToString(" — ")

/**
 * The evolucion's whole treatment as text: one line per prescription, then the free-text
 * indications. The form saves a blank Rx as "—", which is dropped when there are prescriptions.
 */
fun Evolucion.treatmentText(): String {
    val freeText = rx.trim().takeUnless { it.isEmpty() || it == "—" }
    return (prescripciones.map { it.toDisplayLine() } + listOfNotNull(freeText)).joinToString("\n")
}

/**
 * A treatment the patient is on now, from a signed evolucion of any of their stays. Suspending it
 * stops it early without amending that evolucion.
 */
data class ActiveTreatment(
    val id: String,
    val drugName: String,
    val dose: String,
    val frequency: String,
    val route: RouteOfAdministration?,
    val episodeId: String,
    val episodeStartDate: LocalDate?,
)

/**
 * The patient's MEDICATION allergies that [drug] matches by name, either way round and ignoring
 * case and accents ("Penicilina" allergy, "Penicilina G" drug). It can't know drug classes, so an
 * allergy to penicillin won't flag amoxicillin.
 */
fun Patient.allergiesMatching(drug: Drug): List<Allergy> {
    val drugName = drug.genericName.normalizedForMatch()
    if (drugName.isEmpty()) return emptyList()
    return allergies.filter { allergy ->
        val allergen = allergy.description.normalizedForMatch()
        allergy.allergyType == AllergyType.MEDICATION && allergen.isNotEmpty() &&
            (drugName.contains(allergen) || allergen.contains(drugName))
    }
}

private val accents = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')

private fun String.normalizedForMatch(): String = trim().lowercase().map { accents[it] ?: it }.joinToString("")
