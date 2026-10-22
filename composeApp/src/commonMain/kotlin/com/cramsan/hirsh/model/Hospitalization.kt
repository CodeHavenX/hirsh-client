package com.cramsan.hirsh.model

import kotlin.time.Instant

/**
 * One hospital stay -- the backend's `EpisodeResponse`/`EpisodeSummary` (HISS-631). Field names
 * follow the API; the class keeps the prototype's name. Its Historia Clinica and evoluciones are
 * not part of it: they live in [com.cramsan.hirsh.repository.ClinicalNoteRepository] (HISS-641).
 *
 * [currentService]/[currentBed]/[attendingPhysician] are references to real backend rows, all
 * nullable as in the API (an outpatient episode has no bed).
 */
data class Hospitalizacion(
    val id: String,
    val patientId: String,
    val currentService: ServiceRef?,
    val currentBed: BedRef?,
    val attendingPhysician: UserRef?,
    val startDate: Instant,
    /** Null while the episode is still [EpisodeStatus.ACTIVE]. */
    val endDate: Instant?,
    val admissionReason: String?,
    val status: EpisodeStatus,
    /**
     * The real backend's optimistic-lock version on this row (HISS-604) -- a write against a
     * stale version (e.g. [com.cramsan.hirsh.repository.EpisodeRepository.discharge]'s
     * `DischargeEpisodeRequest` equivalent) is rejected with
     * [com.cramsan.hirsh.network.ApiError.Conflict] rather than silently overwriting a
     * concurrent edit. See [Patient.jpaVersion]'s doc comment.
     */
    val jpaVersion: Long = 0L,
    /** Set once the episode is discharged (HISS-633); null while it's [EpisodeStatus.ACTIVE]. */
    val discharge: DischargeDetails? = null,
)

/**
 * What the backend's `DischargeEpisodeRequest` records when an episode closes (HISS-633), and what
 * `EpisodeResponse` returns once it has. [diagnosisIcd10] must exist in the ICD-10 catalog.
 * [nextAppointmentDate] is `dd/MM/yyyy`, like [Patient.birthDate], converted only at the API
 * boundary.
 */
data class DischargeDetails(
    val type: DischargeType,
    val condition: DischargeCondition,
    val diagnosisIcd10: String,
    val followUpPlan: String? = null,
    val nextAppointmentDate: String? = null,
)

/** Mirrors the backend's `DischargeType`. */
enum class DischargeType { MEDICAL, VOLUNTARY, ELOPEMENT, TRANSFER, DEATH, AGAINST_ADVICE }

fun DischargeType.toDisplayLabel(): String = when (this) {
    DischargeType.MEDICAL -> "Alta medica"
    DischargeType.VOLUNTARY -> "Alta voluntaria"
    DischargeType.ELOPEMENT -> "Fuga"
    DischargeType.TRANSFER -> "Transferencia"
    DischargeType.DEATH -> "Fallecimiento"
    DischargeType.AGAINST_ADVICE -> "Retiro contra opinion medica"
}

/** Mirrors the backend's `DischargeCondition`: the patient's condition at discharge. */
enum class DischargeCondition { IMPROVED, RECOVERED, UNCHANGED, WORSENED, DECEASED }

fun DischargeCondition.toDisplayLabel(): String = when (this) {
    DischargeCondition.IMPROVED -> "Mejorado"
    DischargeCondition.RECOVERED -> "Recuperado"
    DischargeCondition.UNCHANGED -> "Sin cambios"
    DischargeCondition.WORSENED -> "Empeorado"
    DischargeCondition.DECEASED -> "Fallecido"
}

/** One ICD-10 catalog entry, as the backend's `Icd10CodeResponse` returns it. */
data class Icd10Code(val code: String, val description: String)

/**
 * Mirrors the backend's `EpisodeStatus`. Discharging sets [TRANSFERRED] for a
 * [DischargeType.TRANSFER] and [CLOSED] for every other type.
 */
enum class EpisodeStatus {
    ACTIVE,

    /** Discharged and closed. */
    CLOSED,

    /** Closed because care moved to another institution. */
    TRANSFERRED,
}

fun EpisodeStatus.toDisplayLabel(): String = when (this) {
    EpisodeStatus.ACTIVE -> "Activa"
    EpisodeStatus.CLOSED -> "Alta"
    EpisodeStatus.TRANSFERRED -> "Transferida"
}

/** The backend's `ServiceRef`: a clinical service (ward/unit). */
data class ServiceRef(val id: String, val name: String)

/** The backend's `BedRef`. */
data class BedRef(val id: String, val number: String)

/** The backend's `UserRef`: a staff account, e.g. the attending physician. */
data class UserRef(val id: String, val fullName: String)

/** Display forms of the nullable references -- every screen shows a missing one as a dash. */
val Hospitalizacion.serviceName: String get() = currentService?.name ?: "—"
val Hospitalizacion.bedNumber: String get() = currentBed?.number ?: "—"
val Hospitalizacion.physicianName: String get() = attendingPhysician?.fullName ?: "—"
