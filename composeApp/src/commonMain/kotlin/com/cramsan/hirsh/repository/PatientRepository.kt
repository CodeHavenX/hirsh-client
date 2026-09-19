package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.FieldChange
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.singleAllergyFromText
import com.cramsan.hirsh.model.summary
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface PatientRepository {
    val patients: StateFlow<List<Patient>>
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
     * identifier (HISS-621); [Patient.medicalRecordNumber] keeps the prototype's own
     * nextPatientId() scheme (max existing numeric suffix + 1, zero-padded to 5) since that's the
     * human-facing identifier a real medical-records system would still assign sequentially.
     * [allergies] stays a single free-text field at the call boundary (HISS-621 defers the real
     * per-allergy CRUD UI to HISS-623): a non-blank, non-"Ninguna" value becomes one synthetic
     * [Allergy] entry. No change-log entry: the prototype's own registerPatient() doesn't call
     * logPatientChange either, registration isn't an edit.
     */
    suspend fun addPatient(
        name: String,
        documentType: DocumentType,
        documentNumber: String,
        birthDate: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
    ): Patient
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
                allergies = singleAllergyFromText("Penicilina"),
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
                allergies = singleAllergyFromText("Sulfas"),
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
        name: String,
        documentType: DocumentType,
        documentNumber: String,
        birthDate: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
    ): Patient {
        val newPatient = Patient(
            id = nextPatientId(),
            medicalRecordNumber = nextMedicalRecordNumber(),
            documentType = documentType,
            documentNumber = documentNumber,
            fullName = name,
            birthDate = birthDate,
            phone = phone,
            sex = sex,
            bloodType = bloodType,
            allergies = singleAllergyFromText(allergies),
        )
        _patients.update { list -> list + newPatient }
        return newPatient
    }

    /** A real UUID, matching the backend's opaque row identifier (HISS-621) -- never `#XXXXX`. */
    @OptIn(ExperimentalUuidApi::class)
    private fun nextPatientId(): String = Uuid.random().toString()

    private fun nextMedicalRecordNumber(): String {
        val maxNumber = _patients.value.maxOfOrNull { it.medicalRecordNumber.removePrefix("HC-").toInt() } ?: 0
        return "HC-" + (maxNumber + 1).toString().padStart(5, '0')
    }
}

private fun diff(oldValue: String, newValue: String, field: String, label: String): FieldChange? =
    if (oldValue == newValue) null else FieldChange(field, label, oldValue, newValue)
