package com.cramsan.hirsh.ui.screens.admision

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
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

private val previewPatient = Patient(
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
)

private class PreviewPatientRepository(private val patient: Patient) : PatientRepository {
    override val patients: StateFlow<List<Patient>> = MutableStateFlow(listOf(patient)).asStateFlow()
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }
    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> =
        MutableStateFlow(emptyList<PatientChangeLogEntry>())

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
    ): Patient = patient
}

private class PreviewHospitalizationRepository : HospitalizationRepository {
    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        MutableStateFlow(emptyList<Hospitalizacion>())

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        MutableStateFlow(null)

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> = MutableStateFlow(null)

    override suspend fun addHospitalization(
        patientId: String,
        servicio: String,
        cama: String,
        medicoResponsable: String,
        motivoIngreso: String,
    ): Hospitalizacion = error("not used in previews")

    override suspend fun discharge(hospId: String) = Unit

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
}

@Preview
@Composable
private fun AdmisionScreenPreview() {
    HirshTheme {
        AdmisionScreen(
            patientId = previewPatient.id,
            onAdmitted = {},
            onCancel = {},
            viewModel = AdmisionViewModel(
                PreviewPatientRepository(previewPatient),
                PreviewHospitalizationRepository(),
            ),
        )
    }
}
