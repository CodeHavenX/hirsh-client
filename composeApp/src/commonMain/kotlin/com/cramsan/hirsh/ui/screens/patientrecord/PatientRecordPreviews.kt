package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.FieldChange
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val previewPatients = listOf(
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
)

private val previewChangeLog = mapOf(
    "#00142" to listOf(
        PatientChangeLogEntry(
            changedBy = "apatel",
            fecha = "05 May 2026",
            hora = "11:20",
            fields = listOf(FieldChange("allergies", "Alergias conocidas", "Ninguna", "Penicilina")),
        ),
    ),
)

private class PreviewPatientRepository(patients: List<Patient>) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }
    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> =
        MutableStateFlow(previewChangeLog[patientId].orEmpty())

    override suspend fun updatePatient(
        id: String,
        newValues: Patient,
        changedBy: String,
        fecha: String,
        hora: String,
    ) = Unit

    override suspend fun addPatient(
        name: String,
        nationalId: String,
        dateOfBirth: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
        assignedDoctor: String,
    ): Patient = Patient(
        id = "#00000",
        name = name,
        dateOfBirth = dateOfBirth,
        phone = phone,
        assignedDoctor = assignedDoctor,
        lastVisit = "—",
        bloodType = bloodType,
        allergies = allergies,
        nationalId = nationalId,
        sex = sex,
    )
}

private fun previewHospitalization(id: String, fechaAlta: String?) = Hospitalizacion(
    id = id,
    patientId = "#00142",
    servicio = "Medicina General",
    cama = "08",
    medicoResponsable = "Dr. Patel",
    fechaIngreso = "10 Abr 2026",
    horaIngreso = "09:00",
    fechaAlta = fechaAlta,
    horaAlta = if (fechaAlta != null) "11:30" else null,
    motivoIngreso = "Tos productiva con fiebre",
    estado = if (fechaAlta != null) EstadoHospitalizacion.ALTA else EstadoHospitalizacion.ACTIVA,
    historiaClinica = HistoriaClinica(
        diagnostico = HcSection(
            complete = true,
            data = Diagnostico(ejeI = "—", ejeII = "—", ejeIII = "Bronquitis aguda (J20.9)", ejeIV = "—", ejeV = "—"),
        ),
        plan = HcSection(
            complete = true,
            data = Plan(
                lugarHospitalizacion = "Medicina General",
                examenesSolicitados = "—",
                psicofarmacos = "—",
                evaluacionesSolicitadas = "—",
            ),
        ),
    ),
    evoluciones = emptyList(),
)

private class PreviewHospitalizationRepository(hospitalizations: List<Hospitalizacion>) : HospitalizationRepository {
    private val _hospitalizations = MutableStateFlow(hospitalizations)

    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        _hospitalizations.map { list -> list.filter { it.patientId == patientId } }

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        _hospitalizations.map { list -> list.find { it.patientId == patientId && it.id == hospId } }

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> =
        _hospitalizations.map { list -> list.find { it.id == hospId }?.evoluciones?.find { it.id == evoId } }

    override suspend fun addHospitalization(
        patientId: String,
        servicio: String,
        cama: String,
        medicoResponsable: String,
        motivoIngreso: String,
    ): Hospitalizacion = previewHospitalization(id = "h_preview", fechaAlta = null)

    override suspend fun discharge(hospId: String) = Unit

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
}

@Preview
@Composable
private fun PatientRecordScreenPreview() {
    HirshTheme {
        PatientRecordScreen(
            patientId = "#00142",
            onEditProfile = {},
            onNewHospitalization = {},
            onHospitalizationSelected = {},
            onViewHistory = {},
            viewModel = PatientRecordViewModel(
                PreviewPatientRepository(previewPatients),
                PreviewHospitalizationRepository(
                    listOf(
                        previewHospitalization("h_gonzalez_1", "12 Abr 2026"),
                        previewHospitalization("h_gonzalez_2", "02 Feb 2026"),
                        previewHospitalization("h_gonzalez_3", "15 Nov 2025"),
                    ),
                ),
            ),
        )
    }
}

/** #00124 has no hospitalizaciones in this preview's fixtures -- exercises the empty state. */
@Preview
@Composable
private fun PatientRecordScreenEmptyHospitalizationsPreview() {
    HirshTheme {
        PatientRecordScreen(
            patientId = "#00124",
            onEditProfile = {},
            onNewHospitalization = {},
            onHospitalizationSelected = {},
            onViewHistory = {},
            viewModel = PatientRecordViewModel(
                PreviewPatientRepository(previewPatients),
                PreviewHospitalizationRepository(emptyList()),
            ),
        )
    }
}
