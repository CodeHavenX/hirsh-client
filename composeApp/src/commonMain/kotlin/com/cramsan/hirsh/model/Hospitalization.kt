package com.cramsan.hirsh.model

import kotlin.time.Instant

/**
 * One hospital stay -- the backend's `EpisodeResponse`/`EpisodeSummary` (HISS-631). Field names
 * follow the API; the class keeps the prototype's name until the repository split (HISS-641).
 *
 * [currentService]/[currentBed]/[attendingPhysician] are references to real backend rows, all
 * nullable as in the API (an outpatient episode has no bed). [historiaClinica]/[evoluciones] have
 * no episode-level equivalent server side and stay client-only until HISS-641 moves them out.
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
    val historiaClinica: HistoriaClinica,
    val evoluciones: List<Evolucion>,
    /**
     * The real backend's optimistic-lock version on this row (HISS-604) -- a write against a
     * stale version (e.g. [com.cramsan.hirsh.repository.HospitalizationRepository.discharge]'s
     * `DischargeEpisodeRequest` equivalent) is rejected with
     * [com.cramsan.hirsh.network.ApiError.Conflict] rather than silently overwriting a
     * concurrent edit. See [Patient.jpaVersion]'s doc comment.
     */
    val jpaVersion: Long = 0L,
)

/** Mirrors the backend's `EpisodeStatus`. */
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
