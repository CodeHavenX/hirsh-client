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
)

enum class EstadoHospitalizacion { ACTIVA, ALTA }
