package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.FieldChange
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

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
     */
    suspend fun updatePatient(id: String, newValues: Patient, changedBy: String, fecha: String, hora: String)
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
                id = "#00142",
                name = "Maria Gonzalez Huerta",
                dateOfBirth = "14/03/1989",
                phone = "987-654-321",
                assignedDoctor = "Dr. Patel",
                lastVisit = "12 Abr 2026",
                bloodType = "O+",
                allergies = "Penicilina",
                nationalId = "45678901",
                sex = Sex.FEMALE,
            ),
            Patient(
                id = "#00138",
                name = "Eduardo Remon Huertas",
                dateOfBirth = "17/07/1962",
                phone = "912-345-678",
                assignedDoctor = "Dr. Reyes",
                lastVisit = "17 Jun 2026",
                bloodType = "A+",
                allergies = "Ninguna",
                nationalId = "09147875",
                sex = Sex.MALE,
            ),
            Patient(
                id = "#00135",
                name = "Jesus Alberto Mendoza Aguilar",
                dateOfBirth = "10/08/1990",
                phone = "955-123-456",
                assignedDoctor = "Dr. Patel",
                lastVisit = "18 Jun 2026",
                bloodType = "B+",
                allergies = "Ninguna",
                nationalId = "70567572",
                sex = Sex.MALE,
            ),
            Patient(
                id = "#00131",
                name = "Maria Santos Vasquez Davila",
                dateOfBirth = "29/01/1943",
                phone = "998-765-432",
                assignedDoctor = "Dr. Lin",
                lastVisit = "19 Jun 2026",
                bloodType = "AB+",
                allergies = "Sulfas",
                nationalId = "07024120",
                sex = Sex.FEMALE,
            ),
            Patient(
                id = "#00129",
                name = "Karla Sofia Ricaldi Sedano",
                dateOfBirth = "15/10/2012",
                phone = "998-984-134",
                assignedDoctor = "Dr. Reyes",
                lastVisit = "20 May 2026",
                bloodType = "—",
                allergies = "Ninguna",
                nationalId = "70083906",
                sex = Sex.FEMALE,
            ),
            Patient(
                id = "#00124",
                name = "Olga Karen Santiesteban Bracamonte",
                dateOfBirth = "12/06/1980",
                phone = "944-556-677",
                assignedDoctor = "Dr. Patel",
                lastVisit = "12 Jun 2026",
                bloodType = "O-",
                allergies = "Ninguna",
                nationalId = "40734432",
                sex = Sex.FEMALE,
            ),
        ),
    )
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()

    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }

    private val _changeLog = MutableStateFlow(
        mapOf(
            "#00142" to listOf(
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
            "#00131" to listOf(
                PatientChangeLogEntry(
                    changedBy = "admin",
                    fecha = "16 Jun 2026",
                    hora = "09:00",
                    fields = listOf(
                        FieldChange(
                            field = "assignedDoctor",
                            label = "Medico asignado",
                            oldValue = "Dr. Reyes",
                            newValue = "Dr. Lin",
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
            "#00138" to listOf(
                PatientChangeLogEntry(
                    changedBy = "slin",
                    fecha = "14 Jun 2026",
                    hora = "10:30",
                    fields = listOf(
                        FieldChange(
                            field = "nationalId",
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
        val changedFields = buildList {
            diff(current.name, newValues.name, "name", "Nombre completo")?.let(::add)
            diff(current.nationalId, newValues.nationalId, "nationalId", "DNI")?.let(::add)
            diff(current.dateOfBirth, newValues.dateOfBirth, "dateOfBirth", "Fecha de nacimiento")?.let(::add)
            diff(current.phone, newValues.phone, "phone", "Telefono de contacto")?.let(::add)
            diff(current.sex.toDisplayLabel(), newValues.sex.toDisplayLabel(), "sex", "Sexo")?.let(::add)
            diff(current.bloodType, newValues.bloodType, "bloodType", "Grupo sanguineo")?.let(::add)
            diff(current.allergies, newValues.allergies, "allergies", "Alergias conocidas")?.let(::add)
            diff(current.assignedDoctor, newValues.assignedDoctor, "assignedDoctor", "Medico asignado")?.let(::add)
        }
        if (changedFields.isEmpty()) return

        _patients.update { list -> list.map { if (it.id == id) newValues else it } }
        _changeLog.update { log ->
            val entry = PatientChangeLogEntry(changedBy = changedBy, fecha = fecha, hora = hora, fields = changedFields)
            log + (id to (listOf(entry) + log[id].orEmpty()))
        }
    }
}

private fun diff(oldValue: String, newValue: String, field: String, label: String): FieldChange? =
    if (oldValue == newValue) null else FieldChange(field, label, oldValue, newValue)

private fun Sex.toDisplayLabel(): String = when (this) {
    Sex.MALE -> "Masculino"
    Sex.FEMALE -> "Femenino"
}
