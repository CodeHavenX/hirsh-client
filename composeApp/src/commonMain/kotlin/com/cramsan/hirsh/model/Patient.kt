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
)

enum class Sex { MALE, FEMALE }
