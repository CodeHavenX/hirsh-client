package com.cramsan.hirsh.model

/**
 * Placeholder shape for the domain object the backend service will eventually own.
 * Field names follow the prototype (`prototype/shared/data.js`) so the mapping from
 * the real API response, once it exists, stays obvious.
 */
data class Hospitalizacion(
    val id: String,
    val patientId: String,
    val servicio: String,
    val cama: String,
    val medicoResponsable: String,
    val fechaIngreso: String,
    val horaIngreso: String,
    val fechaAlta: String?,
    val horaAlta: String?,
    val motivoIngreso: String,
    val estado: EstadoHospitalizacion,
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

enum class EstadoHospitalizacion { ACTIVA, ALTA }
