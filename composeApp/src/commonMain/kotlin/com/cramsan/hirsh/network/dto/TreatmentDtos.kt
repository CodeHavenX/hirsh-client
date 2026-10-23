package com.cramsan.hirsh.network.dto

import com.cramsan.hirsh.model.ActiveTreatment
import com.cramsan.hirsh.model.Drug
import com.cramsan.hirsh.model.RouteOfAdministration
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** One entry of `GET /api/v1/drugs` -- schema `DrugResponse`. */
@Serializable
data class DrugResponse(
    val id: String,
    val genericName: String,
    val concentration: String? = null,
    val pharmaceuticalForm: String? = null,
    val routeOfAdministration: String? = null,
    val therapeuticGroup: String? = null,
    val isPsychotropic: Boolean = false,
)

fun DrugResponse.toDomain(): Drug = Drug(
    id = id,
    genericName = genericName,
    concentration = concentration,
    pharmaceuticalForm = pharmaceuticalForm,
    route = routeOfAdministration.toRoute(),
    therapeuticGroup = therapeuticGroup,
    isPsychotropic = isPsychotropic,
)

/** One entry of `GET /api/v1/patients/{id}/treatments/active` -- schema `ActiveTreatmentSummary`. */
@Serializable
data class ActiveTreatmentSummaryResponse(
    val id: String,
    val drugName: String,
    val dose: String,
    val frequency: String,
    val routeOfAdministration: String? = null,
    val episodeId: String,
    val episodeStartDate: LocalDate? = null,
)

fun ActiveTreatmentSummaryResponse.toDomain(): ActiveTreatment = ActiveTreatment(
    id = id,
    drugName = drugName,
    dose = dose,
    frequency = frequency,
    route = routeOfAdministration.toRoute(),
    episodeId = episodeId,
    episodeStartDate = episodeStartDate,
)

/** Body of `PUT /api/v1/prescribed-treatments/{id}/suspension`. */
@Serializable
data class SuspendTreatmentRequest(
    val suspensionReason: String,
    val endDate: LocalDate,
)

/** The formulary stores the route as free text; anything that isn't a known route name is dropped. */
private fun String?.toRoute(): RouteOfAdministration? =
    this?.trim()?.uppercase()?.let { name -> RouteOfAdministration.entries.find { it.name == name } }
