package com.cramsan.hirsh.ui.screens.patientlist

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme

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

@Preview
@Composable
private fun PatientListScreenPreview() {
    HirshTheme {
        PatientListScreenContent(
            uiState = PatientListUiState(isLoading = false, patients = previewPatients),
            onPatientSelected = {},
            onRegisterPatient = {},
            onQueryChange = {},
        )
    }
}

@Preview
@Composable
private fun PatientListScreenEmptyPreview() {
    HirshTheme {
        PatientListScreenContent(
            uiState = PatientListUiState(isLoading = false, patients = emptyList()),
            onPatientSelected = {},
            onRegisterPatient = {},
            onQueryChange = {},
        )
    }
}
