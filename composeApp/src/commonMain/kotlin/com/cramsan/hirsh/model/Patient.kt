package com.cramsan.hirsh.model

/**
 * Field names follow the backend's `PatientResponse` (HISS-621), so the mapping from the real API
 * response stays obvious once HISS-622 wires a real repository. `assignedDoctor`/`lastVisit` from
 * the old prototype-derived shape are gone entirely -- neither has any equivalent on
 * `PatientResponse` (that's episode/admission data, not patient demographics), and displaying
 * permanently-fake data once this model claims to match the real contract would be dishonest.
 *
 * Two `PatientResponse` fields are deliberately not modeled yet: `age` (server-computed --
 * [calculateAge] keeps deriving it client-side from [birthDate] until a real repository actually
 * returns one) and `emergencyContactData` (free-form JSON server-side with no fixed shape and no
 * consumer here yet -- modeling it now would be a guess with nothing to validate it against).
 */
data class Patient(
    val id: String,
    val medicalRecordNumber: String,
    val documentType: DocumentType,
    val documentNumber: String,
    val firstName: String = "",
    val lastName: String = "",
    val secondLastName: String = "",
    val fullName: String,
    val birthDate: String,
    val phone: String = "",
    val sex: Sex,
    val bloodType: String = "",
    val maritalStatus: String = "",
    val educationLevel: String = "",
    val occupation: String = "",
    val nativeLanguage: String = "",
    val religion: String = "",
    val placeOfBirth: String = "",
    val address: String = "",
    val districtCode: String = "",
    val district: String = "",
    val city: String = "",
    val stateRegion: String = "",
    val email: String = "",
    val insuranceType: String = "",
    val insuranceCode: String = "",
    val photoUrl: String? = null,
    val allergies: List<Allergy> = emptyList(),
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

enum class Sex { MALE, FEMALE, UNKNOWN }

fun Sex.toDisplayLabel(): String = when (this) {
    Sex.MALE -> "Masculino"
    Sex.FEMALE -> "Femenino"
    Sex.UNKNOWN -> "No especifica"
}

/** Mirrors the backend's fixed identity-document catalog (`documentType` on `PatientResponse`/`CreatePatientRequest`). */
enum class DocumentType { NID, FOREIGN_ID, PASSPORT, BIRTH_CERT, UNDOCUMENTED }

fun DocumentType.toDisplayLabel(): String = when (this) {
    DocumentType.NID -> "DNI"
    DocumentType.FOREIGN_ID -> "Carnet de extranjeria"
    DocumentType.PASSPORT -> "Pasaporte"
    DocumentType.BIRTH_CERT -> "Partida de nacimiento"
    DocumentType.UNDOCUMENTED -> "Sin documento"
}
