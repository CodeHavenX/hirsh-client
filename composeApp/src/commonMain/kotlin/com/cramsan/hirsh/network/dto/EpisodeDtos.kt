package com.cramsan.hirsh.network.dto

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DischargeCondition
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DischargeType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.UserRef
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Body of `POST /api/v1/episodes` -- schema `CreateEpisodeRequest`. Only the fields the admission
 * form collects are sent; `admissionType`/`referralSource` have no input yet, and `startDate` is
 * left to the server's own clock (the same "callers never supply it" rule as
 * [com.cramsan.hirsh.repository.HospitalizationRepository.addHospitalization]).
 */
@Serializable
data class CreateEpisodeRequest(
    val patientId: String,
    val episodeType: EpisodeTypeDto,
    val attendingPhysicianId: String,
    val serviceId: String? = null,
    val bedId: String? = null,
    val admissionReason: String? = null,
)

/**
 * Body of `POST /api/v1/episodes/{episodeId}/discharge` -- schema `DischargeEpisodeRequest`.
 * `endDate` is left to the server's clock, like [CreateEpisodeRequest]'s `startDate`.
 * [nextAppointmentDate] is ISO (`2026-07-17`).
 */
@Serializable
data class DischargeEpisodeRequest(
    val dischargeType: DischargeTypeDto,
    val dischargeCondition: DischargeConditionDto,
    val dischargeDiagnosisIcd10: String,
    val followUpPlan: String? = null,
    val nextAppointmentDate: String? = null,
    val jpaVersion: Long,
)

/** 200/201 body of the single-episode endpoints -- schema `EpisodeResponse`. */
@Serializable
data class EpisodeResponse(
    val id: String,
    val patient: PatientHeader,
    val episodeType: EpisodeTypeDto,
    val status: EpisodeStatusDto,
    val startDate: String,
    val endDate: String? = null,
    val admissionReason: String? = null,
    val attendingPhysician: UserRefDto? = null,
    val currentService: ServiceRefDto? = null,
    val currentBed: BedRefDto? = null,
    val dischargeType: DischargeTypeDto? = null,
    val dischargeCondition: DischargeConditionDto? = null,
    val dischargeDiagnosisIcd10: String? = null,
    val followUpPlan: String? = null,
    val nextAppointmentDate: String? = null,
    val audit: AuditBlock,
)

/**
 * One entry of `GET /api/v1/patients/{patientId}/episodes` -- schema `EpisodeSummary`. Of the
 * narrative text it carries only [admissionReason] (the list row's summary line, added for
 * HCEM-73), and it has no audit block, so an episode mapped from it has a placeholder
 * [Hospitalizacion.jpaVersion]; anything that writes against the version reads the full
 * [EpisodeResponse] first.
 */
@Serializable
data class EpisodeSummary(
    val id: String,
    val patient: PatientHeader,
    val episodeType: EpisodeTypeDto,
    val status: EpisodeStatusDto,
    val startDate: String,
    val endDate: String? = null,
    val admissionReason: String? = null,
    val attendingPhysician: UserRefDto? = null,
    val currentService: ServiceRefDto? = null,
    val currentBed: BedRefDto? = null,
)

/** Schema `PatientHeader`, embedded in every episode response. Only [id] is mapped. */
@Serializable
data class PatientHeader(
    val id: String,
    val medicalRecordNumber: String,
    val fullName: String,
)

/** Schema `UserRef`. */
@Serializable
data class UserRefDto(val id: String, val fullName: String)

/** Schema `ServiceRef`. Its short `code` has no use on any screen yet, so it isn't mapped. */
@Serializable
data class ServiceRefDto(val id: String, val code: String, val name: String)

/** Schema `BedRef`. */
@Serializable
data class BedRefDto(val id: String, val number: String)

/**
 * Wire values of the backend's `EpisodeType`. Hospital stays are [INPATIENT] episodes only: the
 * repository lists and opens nothing else, and only [INPATIENT] has the one-open-episode rule.
 */
@Serializable
enum class EpisodeTypeDto { EMERGENCY, INPATIENT, OUTPATIENT, INTERCONSULTATION }

/** Wire values match [EpisodeStatus]'s constant names exactly (confirmed against the backend's `EpisodeStatus.kt`). */
@Serializable
enum class EpisodeStatusDto { ACTIVE, CLOSED, TRANSFERRED }

fun EpisodeStatusDto.toDomain(): EpisodeStatus = when (this) {
    EpisodeStatusDto.ACTIVE -> EpisodeStatus.ACTIVE
    EpisodeStatusDto.CLOSED -> EpisodeStatus.CLOSED
    EpisodeStatusDto.TRANSFERRED -> EpisodeStatus.TRANSFERRED
}

/** Wire values match [DischargeType]'s constant names exactly (confirmed against the backend's `DischargeType.kt`). */
@Serializable
enum class DischargeTypeDto { MEDICAL, VOLUNTARY, ELOPEMENT, TRANSFER, DEATH, AGAINST_ADVICE }

fun DischargeTypeDto.toDomain(): DischargeType = when (this) {
    DischargeTypeDto.MEDICAL -> DischargeType.MEDICAL
    DischargeTypeDto.VOLUNTARY -> DischargeType.VOLUNTARY
    DischargeTypeDto.ELOPEMENT -> DischargeType.ELOPEMENT
    DischargeTypeDto.TRANSFER -> DischargeType.TRANSFER
    DischargeTypeDto.DEATH -> DischargeType.DEATH
    DischargeTypeDto.AGAINST_ADVICE -> DischargeType.AGAINST_ADVICE
}

fun DischargeType.toDto(): DischargeTypeDto = when (this) {
    DischargeType.MEDICAL -> DischargeTypeDto.MEDICAL
    DischargeType.VOLUNTARY -> DischargeTypeDto.VOLUNTARY
    DischargeType.ELOPEMENT -> DischargeTypeDto.ELOPEMENT
    DischargeType.TRANSFER -> DischargeTypeDto.TRANSFER
    DischargeType.DEATH -> DischargeTypeDto.DEATH
    DischargeType.AGAINST_ADVICE -> DischargeTypeDto.AGAINST_ADVICE
}

/** Wire values match [DischargeCondition]'s constant names exactly (confirmed against the backend's `DischargeCondition.kt`). */
@Serializable
enum class DischargeConditionDto { IMPROVED, RECOVERED, UNCHANGED, WORSENED, DECEASED }

fun DischargeConditionDto.toDomain(): DischargeCondition = when (this) {
    DischargeConditionDto.IMPROVED -> DischargeCondition.IMPROVED
    DischargeConditionDto.RECOVERED -> DischargeCondition.RECOVERED
    DischargeConditionDto.UNCHANGED -> DischargeCondition.UNCHANGED
    DischargeConditionDto.WORSENED -> DischargeCondition.WORSENED
    DischargeConditionDto.DECEASED -> DischargeCondition.DECEASED
}

fun DischargeCondition.toDto(): DischargeConditionDto = when (this) {
    DischargeCondition.IMPROVED -> DischargeConditionDto.IMPROVED
    DischargeCondition.RECOVERED -> DischargeConditionDto.RECOVERED
    DischargeCondition.UNCHANGED -> DischargeConditionDto.UNCHANGED
    DischargeCondition.WORSENED -> DischargeConditionDto.WORSENED
    DischargeCondition.DECEASED -> DischargeConditionDto.DECEASED
}

fun DischargeDetails.toRequest(jpaVersion: Long): DischargeEpisodeRequest = DischargeEpisodeRequest(
    dischargeType = type.toDto(),
    dischargeCondition = condition.toDto(),
    dischargeDiagnosisIcd10 = diagnosisIcd10,
    followUpPlan = followUpPlan,
    nextAppointmentDate = nextAppointmentDate?.displayDateToIso(),
    jpaVersion = jpaVersion,
)

/** Null until the episode is discharged -- the backend sets all three required fields together. */
private fun EpisodeResponse.dischargeDetails(): DischargeDetails? {
    val type = dischargeType ?: return null
    val condition = dischargeCondition ?: return null
    val diagnosis = dischargeDiagnosisIcd10 ?: return null
    return DischargeDetails(
        type = type.toDomain(),
        condition = condition.toDomain(),
        diagnosisIcd10 = diagnosis,
        followUpPlan = followUpPlan,
        nextAppointmentDate = nextAppointmentDate?.isoDateToDisplay(),
    )
}

fun UserRefDto.toDomain(): UserRef = UserRef(id = id, fullName = fullName)

fun ServiceRefDto.toDomain(): ServiceRef = ServiceRef(id = id, name = name)

fun BedRefDto.toDomain(): BedRef = BedRef(id = id, number = number)

/**
 * Wire timestamps are `OffsetDateTime`s (e.g. `2026-06-01T10:00:00-05:00`); [Instant.parse]
 * accepts the offset form. [Hospitalizacion.historiaClinica]/[Hospitalizacion.evoluciones] have no
 * server-side equivalent (HISS-641), so a real episode always starts with both empty.
 */
fun EpisodeResponse.toDomain(): Hospitalizacion = Hospitalizacion(
    id = id,
    patientId = patient.id,
    currentService = currentService?.toDomain(),
    currentBed = currentBed?.toDomain(),
    attendingPhysician = attendingPhysician?.toDomain(),
    startDate = Instant.parse(startDate),
    endDate = endDate?.let(Instant::parse),
    admissionReason = admissionReason,
    status = status.toDomain(),
    historiaClinica = HistoriaClinica(),
    evoluciones = emptyList(),
    jpaVersion = audit.jpaVersion,
    discharge = dischargeDetails(),
)

/** See [EpisodeSummary]'s doc comment for the field it can't fill. */
fun EpisodeSummary.toDomain(): Hospitalizacion = Hospitalizacion(
    id = id,
    patientId = patient.id,
    currentService = currentService?.toDomain(),
    currentBed = currentBed?.toDomain(),
    attendingPhysician = attendingPhysician?.toDomain(),
    startDate = Instant.parse(startDate),
    endDate = endDate?.let(Instant::parse),
    admissionReason = admissionReason,
    status = status.toDomain(),
    historiaClinica = HistoriaClinica(),
    evoluciones = emptyList(),
)
