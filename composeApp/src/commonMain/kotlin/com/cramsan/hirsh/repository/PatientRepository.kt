package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.FieldChange
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.summary
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.dto.AllergySummary
import com.cramsan.hirsh.network.dto.CreateAllergyRequest
import com.cramsan.hirsh.network.dto.CreatePatientRequest
import com.cramsan.hirsh.network.dto.PageResponse
import com.cramsan.hirsh.network.dto.PatchAllergyRequest
import com.cramsan.hirsh.network.dto.PatchPatientRequest
import com.cramsan.hirsh.network.dto.PatientResponse
import com.cramsan.hirsh.network.dto.displayDateToIso
import com.cramsan.hirsh.network.dto.toDomain
import com.cramsan.hirsh.network.dto.toDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface PatientRepository {
    val patients: StateFlow<List<Patient>>

    /**
     * (Re)populates [patients] -- mirrors [SessionRepository.restore]'s shape (HISS-611): an
     * explicit suspend function the caller invokes once, rather than a repository launching its
     * own background work on a self-owned scope, which can't be deterministically awaited from a
     * test's `runTest` and has no natural place to report failure to. Callers that need [patients]
     * populated (currently just
     * [PatientListViewModel][com.cramsan.hirsh.ui.screens.patientlist.PatientListViewModel])
     * invoke this once via `viewModelScope.launch { }` in `init`, the same way `AppNavHost` calls
     * `restore()`. A no-op for [InMemoryPatientRepository], whose [patients] is already fully
     * populated at construction.
     */
    suspend fun refresh()

    fun getPatient(id: String): Flow<Patient?>
    fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>>

    /**
     * Diffs [newValues] against the current patient and appends one log entry
     * grouping every field that actually changed -- a no-op save (nothing
     * differs) does not append an empty entry, matching the prototype's own
     * logPatientChange()'s `if (!fieldsChanged.length) return`. [changedBy],
     * [fecha], [hora] are supplied by the caller rather than read from
     * session/clock state here -- this repository stays session- and
     * clock-agnostic (see HISS-108's explicit-parameter rule; HISS-110's Clock
     * abstraction doesn't exist yet, and isn't this ticket's dependency).
     *
     * [newValues.jpaVersion][Patient.jpaVersion] carries the version the caller last read (see
     * [EditPatientViewModel][com.cramsan.hirsh.ui.screens.patientedit.EditPatientViewModel]'s
     * `original.copy(...)`), not a value the caller edits itself -- a mismatch against the
     * currently stored version throws [com.cramsan.hirsh.network.ApiException] wrapping
     * [com.cramsan.hirsh.network.ApiError.Conflict] instead of silently overwriting a concurrent
     * edit (HISS-604).
     */
    suspend fun updatePatient(id: String, newValues: Patient, changedBy: String, fecha: String, hora: String)

    /**
     * Creates a new patient. [Patient.id] is a fresh UUID, matching the backend's opaque row
     * identifier (HISS-621). [medicalRecordNumber] is caller-supplied (HISS-622: confirmed against
     * the real backend that this is the hospital's own legacy/paper number, never server-generated)
     * -- [RegisterPatientViewModel][com.cramsan.hirsh.ui.screens.patientregister.RegisterPatientViewModel]
     * collects it as a real required field. [firstName]/[lastName] are required, [secondLastName]
     * optional, matching `CreatePatientRequest`'s exact shape; implementations assemble [Patient.fullName]
     * themselves when there's no server response to take it from. The new patient
     * starts with no allergies: registration posts each one afterwards via [addAllergy] (HISS-625).
     * No change-log entry: the prototype's own registerPatient() doesn't call logPatientChange either,
     * registration isn't an edit.
     */
    suspend fun addPatient(
        medicalRecordNumber: String,
        firstName: String,
        lastName: String,
        secondLastName: String,
        documentType: DocumentType,
        documentNumber: String,
        birthDate: String,
        phone: String,
        sex: Sex,
        bloodType: String,
    ): Patient

    /**
     * Records one allergy on [patientId] (`POST .../allergies`) and returns it as stored. Every
     * allergy call below is its own request against the sub-resource, saved immediately --
     * independent of [updatePatient], which never touches [Patient.allergies] (HISS-623). All
     * three keep [patients]/[getPatient] in sync with the change, so a screen observing the same
     * patient reflects it without re-fetching.
     */
    suspend fun addAllergy(
        patientId: String,
        allergyType: AllergyType,
        description: String,
        severity: Severity?,
        observations: String,
    ): Allergy

    /**
     * Revises an allergy's [severity]/[observations] -- the only two fields the real
     * `PatchAllergyRequest` accepts (see [Allergy]'s doc comment on why type/description are
     * immutable).
     */
    suspend fun updateAllergy(patientId: String, allergyId: String, severity: Severity?, observations: String): Allergy

    /** Removes an allergy recorded in error (a logical delete server-side). */
    suspend fun deleteAllergy(patientId: String, allergyId: String)
}

/**
 * In-memory stand-in, seeded from prototype/shared/data.js, so the patient list and
 * navigation flow are demonstrable before the backend service (separate repo) exists.
 * Replace with an implementation backed by [io.ktor.client.HttpClient] once that
 * service exposes a patients endpoint.
 */
class InMemoryPatientRepository : PatientRepository {

    private val _patients = MutableStateFlow(
        listOf(
            Patient(
                id = "07c98942-3654-4034-b960-f3265814e214",
                medicalRecordNumber = "HC-00142",
                documentType = DocumentType.NID,
                documentNumber = "45678901",
                fullName = "Maria Gonzalez Huerta",
                birthDate = "14/03/1989",
                phone = "987-654-321",
                bloodType = "O+",
                allergies = listOf(
                    Allergy(
                        id = "5b0f6c52-3a8e-4f4e-9d62-1c7a2e8b9f10",
                        allergyType = AllergyType.MEDICATION,
                        description = "Penicilina",
                        severity = Severity.SEVERE,
                    ),
                ),
                sex = Sex.FEMALE,
            ),
            Patient(
                id = "a21f8bfa-299c-42db-9681-c84f87a90ce4",
                medicalRecordNumber = "HC-00138",
                documentType = DocumentType.NID,
                documentNumber = "09147875",
                fullName = "Eduardo Remon Huertas",
                birthDate = "17/07/1962",
                phone = "912-345-678",
                bloodType = "A+",
                allergies = emptyList(),
                sex = Sex.MALE,
            ),
            Patient(
                id = "1e0697d0-f3e4-4755-85ad-d888d2b15f9d",
                medicalRecordNumber = "HC-00135",
                documentType = DocumentType.NID,
                documentNumber = "70567572",
                fullName = "Jesus Alberto Mendoza Aguilar",
                birthDate = "10/08/1990",
                phone = "955-123-456",
                bloodType = "B+",
                allergies = emptyList(),
                sex = Sex.MALE,
            ),
            Patient(
                id = "caaedede-5d72-4a52-bb72-7532d7cdda83",
                medicalRecordNumber = "HC-00131",
                documentType = DocumentType.NID,
                documentNumber = "07024120",
                fullName = "Maria Santos Vasquez Davila",
                birthDate = "29/01/1943",
                phone = "998-765-432",
                bloodType = "AB+",
                allergies = listOf(
                    Allergy(
                        id = "c3d1e9a4-7b25-4c0e-8f3a-6d9e2b1a4c77",
                        allergyType = AllergyType.MEDICATION,
                        description = "Sulfas",
                        severity = null,
                    ),
                ),
                sex = Sex.FEMALE,
            ),
            Patient(
                id = "30c14d79-8c7e-43f8-870c-f1dbc4c90247",
                medicalRecordNumber = "HC-00129",
                documentType = DocumentType.NID,
                documentNumber = "70083906",
                fullName = "Karla Sofia Ricaldi Sedano",
                birthDate = "15/10/2012",
                phone = "998-984-134",
                bloodType = "—",
                allergies = emptyList(),
                sex = Sex.FEMALE,
            ),
            Patient(
                id = "a7efc7af-d998-43e8-8abd-d07c1155ef9f",
                medicalRecordNumber = "HC-00124",
                documentType = DocumentType.NID,
                documentNumber = "40734432",
                fullName = "Olga Karen Santiesteban Bracamonte",
                birthDate = "12/06/1980",
                phone = "944-556-677",
                bloodType = "O-",
                allergies = emptyList(),
                sex = Sex.FEMALE,
            ),
        ),
    )
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()

    /** No-op: [_patients] is already fully populated from the seed fixtures at construction. */
    override suspend fun refresh() = Unit

    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }

    private val _changeLog = MutableStateFlow(
        mapOf(
            "07c98942-3654-4034-b960-f3265814e214" to listOf(
                PatientChangeLogEntry(
                    changedBy = "apatel",
                    fecha = "05 May 2026",
                    hora = "11:20",
                    fields = listOf(
                        FieldChange(
                            field = "allergies",
                            label = "Alergias conocidas",
                            oldValue = "Ninguna",
                            newValue = "Penicilina",
                        ),
                    ),
                ),
                PatientChangeLogEntry(
                    changedBy = "mreyes",
                    fecha = "13 Abr 2026",
                    hora = "08:30",
                    fields = listOf(
                        FieldChange(
                            field = "phone",
                            label = "Telefono de contacto",
                            oldValue = "987-654-320",
                            newValue = "987-654-321",
                        ),
                    ),
                ),
            ),
            "caaedede-5d72-4a52-bb72-7532d7cdda83" to listOf(
                PatientChangeLogEntry(
                    changedBy = "admin",
                    fecha = "16 Jun 2026",
                    hora = "09:00",
                    fields = listOf(
                        FieldChange(
                            field = "district",
                            label = "Distrito",
                            oldValue = "—",
                            newValue = "San Martin de Porres",
                        ),
                    ),
                ),
                PatientChangeLogEntry(
                    changedBy = "mreyes",
                    fecha = "15 Jun 2026",
                    hora = "20:00",
                    fields = listOf(
                        FieldChange(
                            field = "bloodType",
                            label = "Grupo sanguineo",
                            oldValue = "—",
                            newValue = "AB+",
                        ),
                    ),
                ),
            ),
            "a21f8bfa-299c-42db-9681-c84f87a90ce4" to listOf(
                PatientChangeLogEntry(
                    changedBy = "slin",
                    fecha = "14 Jun 2026",
                    hora = "10:30",
                    fields = listOf(
                        FieldChange(
                            field = "documentNumber",
                            label = "DNI",
                            oldValue = "09147785",
                            newValue = "09147875",
                        ),
                    ),
                ),
            ),
        ),
    )

    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> =
        _changeLog.map { it[patientId].orEmpty() }

    override suspend fun updatePatient(
        id: String,
        newValues: Patient,
        changedBy: String,
        fecha: String,
        hora: String,
    ) {
        val current = _patients.value.find { it.id == id } ?: return
        if (current.jpaVersion != newValues.jpaVersion) {
            throw ApiException(ApiError.Conflict(id))
        }
        val changedFields = buildList {
            diff(current.fullName, newValues.fullName, "fullName", "Nombre completo")?.let(::add)
            diff(current.documentType.toDisplayLabel(), newValues.documentType.toDisplayLabel(), "documentType", "Tipo de documento")?.let(::add)
            diff(current.documentNumber, newValues.documentNumber, "documentNumber", "DNI")?.let(::add)
            diff(current.birthDate, newValues.birthDate, "birthDate", "Fecha de nacimiento")?.let(::add)
            diff(current.phone, newValues.phone, "phone", "Telefono de contacto")?.let(::add)
            diff(current.sex.toDisplayLabel(), newValues.sex.toDisplayLabel(), "sex", "Sexo")?.let(::add)
            diff(current.bloodType, newValues.bloodType, "bloodType", "Grupo sanguineo")?.let(::add)
            diff(current.allergies.summary(), newValues.allergies.summary(), "allergies", "Alergias conocidas")?.let(::add)
        }
        if (changedFields.isEmpty()) return

        _patients.update { list -> list.map { if (it.id == id) newValues.copy(jpaVersion = current.jpaVersion + 1) else it } }
        _changeLog.update { log ->
            val entry = PatientChangeLogEntry(changedBy = changedBy, fecha = fecha, hora = hora, fields = changedFields)
            log + (id to (listOf(entry) + log[id].orEmpty()))
        }
    }

    override suspend fun addPatient(
        medicalRecordNumber: String,
        firstName: String,
        lastName: String,
        secondLastName: String,
        documentType: DocumentType,
        documentNumber: String,
        birthDate: String,
        phone: String,
        sex: Sex,
        bloodType: String,
    ): Patient {
        val newPatient = Patient(
            id = nextId(),
            medicalRecordNumber = medicalRecordNumber,
            documentType = documentType,
            documentNumber = documentNumber,
            firstName = firstName,
            lastName = lastName,
            secondLastName = secondLastName,
            fullName = assembleFullName(firstName, lastName, secondLastName),
            birthDate = birthDate,
            phone = phone,
            sex = sex,
            bloodType = bloodType,
            allergies = emptyList(),
        )
        _patients.update { list -> list + newPatient }
        return newPatient
    }

    override suspend fun addAllergy(
        patientId: String,
        allergyType: AllergyType,
        description: String,
        severity: Severity?,
        observations: String,
    ): Allergy {
        requirePatient(patientId)
        val allergy = Allergy(
            id = nextId(),
            allergyType = allergyType,
            description = description.trim(),
            severity = severity,
            observations = observations,
        )
        // Newest first, matching the real list endpoint's `ORDER BY created_at DESC`.
        updateAllergies(patientId) { listOf(allergy) + it }
        return allergy
    }

    override suspend fun updateAllergy(
        patientId: String,
        allergyId: String,
        severity: Severity?,
        observations: String,
    ): Allergy {
        val current = requirePatient(patientId).allergies.find { it.id == allergyId }
            ?: throw ApiException(ApiError.NotFound(allergyId))
        val updated = current.copy(severity = severity, observations = observations)
        updateAllergies(patientId) { list -> list.map { if (it.id == allergyId) updated else it } }
        return updated
    }

    override suspend fun deleteAllergy(patientId: String, allergyId: String) {
        if (requirePatient(patientId).allergies.none { it.id == allergyId }) {
            throw ApiException(ApiError.NotFound(allergyId))
        }
        updateAllergies(patientId) { list -> list.filterNot { it.id == allergyId } }
    }

    private fun requirePatient(patientId: String): Patient =
        _patients.value.find { it.id == patientId } ?: throw ApiException(ApiError.NotFound(patientId))

    private fun updateAllergies(patientId: String, transform: (List<Allergy>) -> List<Allergy>) {
        _patients.update { list -> list.map { if (it.id == patientId) it.copy(allergies = transform(it.allergies)) else it } }
    }

    /** A real UUID, matching the backend's opaque row identifier (HISS-621) -- never `#XXXXX`. */
    @OptIn(ExperimentalUuidApi::class)
    private fun nextId(): String = Uuid.random().toString()
}

/**
 * Real implementation, backed by the shared `HttpClient` (HISS-622). See `network/dto/PatientDtos.kt`
 * for the DTOs mapped here.
 *
 * [patients] has no server-push equivalent, so [refresh] (from the [PatientRepository] interface --
 * see its own doc comment on why this is an explicit suspend function rather than a background
 * launch this class would manage itself) does one "fetch all pages" pass via [fetchAllPages] and
 * replaces [_patients] wholesale. Callers that mutate (create/update) instead patch [_patients]
 * directly from that call's own response, matching [InMemoryPatientRepository]'s own
 * update-in-place pattern -- no need to re-fetch everything just to reflect one changed row.
 *
 * [getPatient] makes its own `GET /{patientId}` call every time it's collected, rather than
 * filtering [patients]'s "fetch all pages" cache -- the ticket lists it as its own endpoint, and a
 * real deployment could have more patients than any single "fetch all" pass caps at ([PAGE_SIZE]).
 * After that fetch it keeps observing [_details], so a later mutation through this repository
 * (an edit, an allergy add/update/delete) reaches an already-open record screen without a
 * re-fetch (HISS-623). [_details] is kept separate from [_patients] so a concurrent [refresh]
 * replacing the list wholesale can never make an open record flip to "not found".
 *
 * [updatePatient]'s [changedBy]/[fecha]/[hora] params go unused: the real `PATCH` endpoint doesn't
 * accept them (the server stamps its own audit block), and `PatientService.patch()` has no
 * optimistic-lock check at all (confirmed reading the backend source) -- a stale [Patient.jpaVersion]
 * can never actually produce a `409` for this endpoint. They stay on the shared interface only
 * because [InMemoryPatientRepository]'s own change-log feature still needs them.
 *
 * [getChangeLog] has no real equivalent yet -- the real audit trail is a differently-shaped
 * `AccessLogController`; HISS-624 ("degrade to access-log view") is the ticket that migrates
 * [PatientHistoryScreen][com.cramsan.hirsh.ui.screens.patienthistory.PatientHistoryScreen] onto it.
 * Returns an always-empty flow here rather than guessing at a mapping.
 */
class KtorPatientRepository(
    private val httpClient: HttpClient,
) : PatientRepository {

    private val _patients = MutableStateFlow<List<Patient>>(emptyList())
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()

    /** Per-patient cache backing [getPatient]; see the class doc comment. */
    private val _details = MutableStateFlow<Map<String, Patient>>(emptyMap())

    override suspend fun refresh() {
        _patients.value = fetchAllPages()
    }

    /** Writes [patient] through to both caches -- [_patients] only if it's already listed there. */
    private fun cache(patient: Patient) {
        _patients.update { list -> list.map { if (it.id == patient.id) patient else it } }
        _details.update { it + (patient.id to patient) }
    }

    private fun updateAllergies(patientId: String, transform: (List<Allergy>) -> List<Allergy>) {
        _patients.update { list -> list.map { if (it.id == patientId) it.copy(allergies = transform(it.allergies)) else it } }
        _details.update { details ->
            val patient = details[patientId] ?: return@update details
            details + (patientId to patient.copy(allergies = transform(patient.allergies)))
        }
    }

    private suspend fun fetchAllPages(): List<Patient> {
        val firstPage = fetchPage(0)
        val pages = buildList {
            addAll(firstPage.content)
            for (page in 1 until firstPage.page.totalPages) {
                addAll(fetchPage(page).content)
            }
        }
        return pages.map { it.toDomain() }
    }

    private suspend fun fetchPage(page: Int): PageResponse<PatientResponse> =
        httpClient.get("/api/v1/patients") {
            parameter("page", page)
            parameter("size", PAGE_SIZE)
        }.body()

    override fun getPatient(id: String): Flow<Patient?> = flow {
        val fetched = try {
            httpClient.get("/api/v1/patients/$id").body<PatientResponse>().toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.error is ApiError.NotFound) null else throw e
        }
        if (fetched == null) {
            emit(null)
            return@flow
        }
        cache(fetched)
        emitAll(_details.map { it[id] }.distinctUntilChanged())
    }

    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> = flowOf(emptyList())

    override suspend fun updatePatient(
        id: String,
        newValues: Patient,
        changedBy: String,
        fecha: String,
        hora: String,
    ) {
        val updated = httpClient.patch("/api/v1/patients/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                PatchPatientRequest(
                    firstName = newValues.firstName,
                    lastName = newValues.lastName,
                    secondLastName = newValues.secondLastName,
                    phone = newValues.phone,
                    bloodType = newValues.bloodType,
                ),
            )
        }.body<PatientResponse>().toDomain()
        cache(updated)
    }

    override suspend fun addPatient(
        medicalRecordNumber: String,
        firstName: String,
        lastName: String,
        secondLastName: String,
        documentType: DocumentType,
        documentNumber: String,
        birthDate: String,
        phone: String,
        sex: Sex,
        bloodType: String,
    ): Patient {
        val response = httpClient.post("/api/v1/patients") {
            contentType(ContentType.Application.Json)
            setBody(
                CreatePatientRequest(
                    medicalRecordNumber = medicalRecordNumber,
                    documentType = documentType.toDto(),
                    documentNumber = documentNumber,
                    firstName = firstName,
                    lastName = lastName,
                    secondLastName = secondLastName,
                    birthDate = birthDate.displayDateToIso(),
                    sex = sex.toDto(),
                    bloodType = bloodType,
                    phone = phone,
                ),
            )
        }.body<PatientResponse>()
        val created = response.toDomain()
        _patients.update { list -> list + created }
        return created
    }

    override suspend fun addAllergy(
        patientId: String,
        allergyType: AllergyType,
        description: String,
        severity: Severity?,
        observations: String,
    ): Allergy {
        val allergy = httpClient.post("/api/v1/patients/$patientId/allergies") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateAllergyRequest(
                    allergyType = allergyType.toDto(),
                    description = description.trim(),
                    severity = severity?.toDto(),
                    observations = observations.ifBlank { null },
                ),
            )
        }.body<AllergySummary>().toDomain()
        // Newest first, matching the real list endpoint's `ORDER BY created_at DESC`.
        updateAllergies(patientId) { listOf(allergy) + it }
        return allergy
    }

    /**
     * Sends both fields every time rather than only the changed ones: a null field means "leave
     * unchanged" server-side, so this is the only way the form's current values are what's stored.
     * A known limit of the real endpoint: a severity can't be reset back to ungraded (null) once
     * set, and observations can't be cleared to null (blank is sent as `""` instead).
     */
    override suspend fun updateAllergy(
        patientId: String,
        allergyId: String,
        severity: Severity?,
        observations: String,
    ): Allergy {
        val updated = httpClient.patch("/api/v1/patients/$patientId/allergies/$allergyId") {
            contentType(ContentType.Application.Json)
            setBody(PatchAllergyRequest(severity = severity?.toDto(), observations = observations))
        }.body<AllergySummary>().toDomain()
        updateAllergies(patientId) { list -> list.map { if (it.id == allergyId) updated else it } }
        return updated
    }

    /** `204 No Content` -- deliberately no `.body()` call, there's nothing to deserialize. */
    override suspend fun deleteAllergy(patientId: String, allergyId: String) {
        httpClient.delete("/api/v1/patients/$patientId/allergies/$allergyId")
        updateAllergies(patientId) { list -> list.filterNot { it.id == allergyId } }
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}

/** Mirrors the real backend's own name assembly (`PatientResponse.fullName`, "already assembled"). */
internal fun assembleFullName(firstName: String, lastName: String, secondLastName: String): String =
    listOf(firstName, lastName, secondLastName).filter { it.isNotBlank() }.joinToString(" ")

private fun diff(oldValue: String, newValue: String, field: String, label: String): FieldChange? =
    if (oldValue == newValue) null else FieldChange(field, label, oldValue, newValue)
