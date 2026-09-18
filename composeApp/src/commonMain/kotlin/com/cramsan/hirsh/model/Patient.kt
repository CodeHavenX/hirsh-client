package com.cramsan.hirsh.model

/**
 * Placeholder shape for the domain object the backend service will eventually own.
 * Field names follow the prototype (`prototype/shared/data.js`) so the mapping from
 * the real API response, once it exists, stays obvious.
 */
data class Patient(
    val id: String,
    val name: String,
    val dateOfBirth: String,
    val phone: String,
    val assignedDoctor: String,
    val lastVisit: String,
    val bloodType: String,
    val allergies: String,
    val nationalId: String,
    val sex: Sex,
    /**
     * The real backend's optimistic-lock version on this row (HISS-604) -- callers editing a
     * patient must send back the version they read; a save against a stale version is rejected
     * with [com.cramsan.hirsh.network.ApiError.Conflict] rather than silently overwriting a
     * concurrent edit. Defaulted so every existing construction site (seed data, test fixtures)
     * keeps compiling; the fakes bump it themselves on a successful update, same as a real
     * `jpaVersion` column would.
     */
    val jpaVersion: Long = 0L,
)

enum class Sex { MALE, FEMALE }

fun Sex.toDisplayLabel(): String = when (this) {
    Sex.MALE -> "Masculino"
    Sex.FEMALE -> "Femenino"
}
