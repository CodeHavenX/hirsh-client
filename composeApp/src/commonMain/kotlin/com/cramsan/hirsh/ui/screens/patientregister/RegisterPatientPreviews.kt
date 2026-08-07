package com.cramsan.hirsh.ui.screens.patientregister

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
import kotlinx.coroutines.flow.update

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

    override suspend fun addPatient(
        name: String,
        nationalId: String,
        dateOfBirth: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
        assignedDoctor: String,
    ): Patient {
        val created = Patient(
            id = "#00200",
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
        _patients.update { it + created }
        return created
    }
}

@Preview
@Composable
private fun RegisterPatientScreenPreview() {
    HirshTheme {
        RegisterPatientScreen(
            onRegistered = {},
            onCancel = {},
            onViewExistingPatient = {},
            viewModel = RegisterPatientViewModel(PreviewPatientRepository(previewPatients)),
        )
    }
}

@Preview
@Composable
private fun RegisterPatientScreenDuplicateWarningPreview() {
    HirshTheme {
        val viewModel = RegisterPatientViewModel(PreviewPatientRepository(previewPatients))
        viewModel.onNameChange("Maria Gonzalez")
        viewModel.checkDuplicate()
        RegisterPatientScreen(
            onRegistered = {},
            onCancel = {},
            onViewExistingPatient = {},
            viewModel = viewModel,
        )
    }
}
