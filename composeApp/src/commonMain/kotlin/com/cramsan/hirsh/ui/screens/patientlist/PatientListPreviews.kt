package com.cramsan.hirsh.ui.screens.patientlist

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Sex
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
)

private class PreviewPatientRepository(patients: List<Patient>) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
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
}

@Preview
@Composable
private fun PatientListScreenPreview() {
    HirshTheme {
        PatientListScreen(
            onPatientSelected = {},
            onRegisterPatient = {},
            viewModel = PatientListViewModel(PreviewPatientRepository(previewPatients)),
        )
    }
}

@Preview
@Composable
private fun PatientListScreenEmptyPreview() {
    HirshTheme {
        PatientListScreen(
            onPatientSelected = {},
            onRegisterPatient = {},
            viewModel = PatientListViewModel(PreviewPatientRepository(emptyList())),
        )
    }
}
