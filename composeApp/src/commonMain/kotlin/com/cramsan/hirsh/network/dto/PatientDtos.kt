package com.cramsan.hirsh.network.dto

import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Body of `POST /api/v1/patients` -- schema `CreatePatientRequest`. Only the fields this client's
 * registration form actually collects are ever non-null; every other field on the real schema
 * (address/insurance/emergency-contact/etc.) is deferred, matching HISS-621's "surface
 * documentType/documentNumber at minimum" decision -- the backend defaults the rest.
 */
@Serializable
data class CreatePatientRequest(
    val medicalRecordNumber: String,
    val documentType: PatientDocumentTypeDto,
    val documentNumber: String,
    val firstName: String,
    val lastName: String,
    val secondLastName: String? = null,
    val birthDate: String,
    val sex: PatientSexDto,
    val bloodType: String? = null,
    val phone: String? = null,
)

/**
 * Body of `PATCH /api/v1/patients/{patientId}` -- schema `PatchPatientRequest`. Identity fields
 * (medicalRecordNumber, document, birthDate, sex) aren't here at all -- the real endpoint doesn't
 * accept them; see [com.cramsan.hirsh.repository.KtorPatientRepository]'s doc comment.
 */
@Serializable
data class PatchPatientRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val secondLastName: String? = null,
    val phone: String? = null,
    val bloodType: String? = null,
)

/** `POST /api/v1/patients/{patientId}/allergies`'s body -- schema `CreateAllergyRequest`. */
@Serializable
data class CreateAllergyRequest(
    val allergyType: AllergyTypeDto,
    val description: String,
    val severity: AllergySeverityDto? = null,
    val observations: String? = null,
)

/**
 * 200/201 body shared by every patient-returning endpoint -- schema `PatientResponse`. [age] is
 * server-computed and intentionally not mapped into [Patient] (which still derives it client-side
 * via [com.cramsan.hirsh.model.calculateAge] -- see `model/Patient.kt`'s doc comment);
 * [emergencyContactData] has no fixed shape and no consumer here yet, so it's dropped too.
 */
@Serializable
data class PatientResponse(
    val id: String,
    val medicalRecordNumber: String,
    val documentType: PatientDocumentTypeDto,
    val documentNumber: String,
    val firstName: String,
    val lastName: String,
    val secondLastName: String? = null,
    val fullName: String,
    val birthDate: String,
    val sex: PatientSexDto,
    val bloodType: String? = null,
    val maritalStatus: String? = null,
    val educationLevel: String? = null,
    val occupation: String? = null,
    val nativeLanguage: String? = null,
    val religion: String? = null,
    val placeOfBirth: String? = null,
    val address: String? = null,
    val districtCode: String? = null,
    val district: String? = null,
    val city: String? = null,
    val stateRegion: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val insuranceType: String? = null,
    val insuranceCode: String? = null,
    val photoUrl: String? = null,
    val allergies: List<AllergySummary> = emptyList(),
    val audit: AuditBlock,
)

/** One entry of [PatientResponse.allergies] -- schema `AllergySummary`. */
@Serializable
data class AllergySummary(
    val id: String,
    val allergyType: AllergyTypeDto,
    val description: String,
    val severity: AllergySeverityDto? = null,
    val observations: String? = null,
)

/** Embedded in every read response -- schema `AuditBlock`. Only [jpaVersion] maps into [Patient]. */
@Serializable
data class AuditBlock(
    val createdAt: String,
    val createdById: String? = null,
    val updatedAt: String? = null,
    val updatedById: String? = null,
    val jpaVersion: Long,
)

/** Standard collection envelope (API design document, section 0.3) -- schema `PageResponse`. */
@Serializable
data class PageResponse<T>(
    val content: List<T>,
    val page: PageMetadata = PageMetadata(),
)

/** Schema `PageMetadata`. */
@Serializable
data class PageMetadata(
    val number: Int = 0,
    val size: Int = 20,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

/**
 * Wire values are `NID`/`FOREIGN_ID`/`PASSPORT`/`BIRTH_CERT`/`UNDOCUMENTED` -- confirmed identical
 * to [DocumentType]'s constant names against the backend's `PatientDocumentType.kt`. Still kept as
 * its own DTO-level enum (not `@Serializable` added to the domain one) so `model/` stays free of
 * `network/`'s serialization concerns, matching this file's other DTOs.
 */
@Serializable
enum class PatientDocumentTypeDto { NID, FOREIGN_ID, PASSPORT, BIRTH_CERT, UNDOCUMENTED }

fun PatientDocumentTypeDto.toDomain(): DocumentType = when (this) {
    PatientDocumentTypeDto.NID -> DocumentType.NID
    PatientDocumentTypeDto.FOREIGN_ID -> DocumentType.FOREIGN_ID
    PatientDocumentTypeDto.PASSPORT -> DocumentType.PASSPORT
    PatientDocumentTypeDto.BIRTH_CERT -> DocumentType.BIRTH_CERT
    PatientDocumentTypeDto.UNDOCUMENTED -> DocumentType.UNDOCUMENTED
}

fun DocumentType.toDto(): PatientDocumentTypeDto = when (this) {
    DocumentType.NID -> PatientDocumentTypeDto.NID
    DocumentType.FOREIGN_ID -> PatientDocumentTypeDto.FOREIGN_ID
    DocumentType.PASSPORT -> PatientDocumentTypeDto.PASSPORT
    DocumentType.BIRTH_CERT -> PatientDocumentTypeDto.BIRTH_CERT
    DocumentType.UNDOCUMENTED -> PatientDocumentTypeDto.UNDOCUMENTED
}

/**
 * Wire values are `M`/`F`/`U` (confirmed against the backend's `PatientSex.kt`, stored as an
 * ordinal in Postgres) -- the one real mismatch against this client's domain [Sex] enum
 * (`MALE`/`FEMALE`/`UNKNOWN`), which keeps its full-word names since [com.cramsan.hirsh.model.toDisplayLabel]
 * and every screen already read better that way. Bridged only here, not by renaming the domain enum.
 */
@Serializable
enum class PatientSexDto { M, F, U }

fun PatientSexDto.toDomain(): Sex = when (this) {
    PatientSexDto.M -> Sex.MALE
    PatientSexDto.F -> Sex.FEMALE
    PatientSexDto.U -> Sex.UNKNOWN
}

fun Sex.toDto(): PatientSexDto = when (this) {
    Sex.MALE -> PatientSexDto.M
    Sex.FEMALE -> PatientSexDto.F
    Sex.UNKNOWN -> PatientSexDto.U
}

/** Wire values match [AllergyType]'s constant names exactly (confirmed against `patient_allergy`'s `CHECK` constraint). */
@Serializable
enum class AllergyTypeDto { MEDICATION, FOOD, ENVIRONMENTAL, OTHER }

fun AllergyTypeDto.toDomain(): AllergyType = when (this) {
    AllergyTypeDto.MEDICATION -> AllergyType.MEDICATION
    AllergyTypeDto.FOOD -> AllergyType.FOOD
    AllergyTypeDto.ENVIRONMENTAL -> AllergyType.ENVIRONMENTAL
    AllergyTypeDto.OTHER -> AllergyType.OTHER
}

fun AllergyType.toDto(): AllergyTypeDto = when (this) {
    AllergyType.MEDICATION -> AllergyTypeDto.MEDICATION
    AllergyType.FOOD -> AllergyTypeDto.FOOD
    AllergyType.ENVIRONMENTAL -> AllergyTypeDto.ENVIRONMENTAL
    AllergyType.OTHER -> AllergyTypeDto.OTHER
}

/** Wire values match [Severity]'s constant names exactly (confirmed against `patient_allergy`'s `CHECK` constraint). */
@Serializable
enum class AllergySeverityDto { MILD, MODERATE, SEVERE }

fun AllergySeverityDto.toDomain(): Severity = when (this) {
    AllergySeverityDto.MILD -> Severity.MILD
    AllergySeverityDto.MODERATE -> Severity.MODERATE
    AllergySeverityDto.SEVERE -> Severity.SEVERE
}

fun Severity.toDto(): AllergySeverityDto = when (this) {
    Severity.MILD -> AllergySeverityDto.MILD
    Severity.MODERATE -> AllergySeverityDto.MODERATE
    Severity.SEVERE -> AllergySeverityDto.SEVERE
}

fun AllergySummary.toDomain(): Allergy = Allergy(
    id = id,
    allergyType = allergyType.toDomain(),
    description = description,
    severity = severity?.toDomain() ?: Severity.MILD,
    observations = observations.orEmpty(),
)

/**
 * Wire dates are ISO-8601 (`java.time.LocalDate`'s default JSON form, e.g. `1991-04-12`); this
 * client's [Patient.birthDate] has always been `dd/MM/yyyy` (mirroring `prototype/shared/data.js`
 * -- see [com.cramsan.hirsh.model.calculateAge]'s own `split("/")`). Bridged only at this boundary,
 * not by changing the display format everywhere it's already used.
 */
fun String.isoDateToDisplay(): String {
    val date = LocalDate.parse(this)
    return "${date.dayOfMonth.pad2()}/${date.monthNumber.pad2()}/${date.year}"
}

/** Inverse of [isoDateToDisplay]. */
fun String.displayDateToIso(): String {
    val (day, month, year) = split("/").map(String::toInt)
    return LocalDate(year, month, day).toString()
}

private fun Int.pad2(): String = toString().padStart(2, '0')

fun PatientResponse.toDomain(): Patient = Patient(
    id = id,
    medicalRecordNumber = medicalRecordNumber,
    documentType = documentType.toDomain(),
    documentNumber = documentNumber,
    firstName = firstName,
    lastName = lastName,
    secondLastName = secondLastName.orEmpty(),
    fullName = fullName,
    birthDate = birthDate.isoDateToDisplay(),
    phone = phone.orEmpty(),
    sex = sex.toDomain(),
    bloodType = bloodType.orEmpty(),
    maritalStatus = maritalStatus.orEmpty(),
    educationLevel = educationLevel.orEmpty(),
    occupation = occupation.orEmpty(),
    nativeLanguage = nativeLanguage.orEmpty(),
    religion = religion.orEmpty(),
    placeOfBirth = placeOfBirth.orEmpty(),
    address = address.orEmpty(),
    districtCode = districtCode.orEmpty(),
    district = district.orEmpty(),
    city = city.orEmpty(),
    stateRegion = stateRegion.orEmpty(),
    email = email.orEmpty(),
    insuranceType = insuranceType.orEmpty(),
    insuranceCode = insuranceCode.orEmpty(),
    photoUrl = photoUrl,
    allergies = allergies.map { it.toDomain() },
    jpaVersion = audit.jpaVersion,
)
