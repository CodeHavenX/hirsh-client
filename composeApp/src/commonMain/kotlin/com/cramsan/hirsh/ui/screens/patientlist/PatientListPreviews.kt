package com.cramsan.hirsh.ui.screens.patientlist

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.singleAllergyFromText
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewPatients = listOf(
    Patient(
        id = "#00142",
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
        id = "#00138",
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
)

@PreviewResponsive
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

@PreviewResponsive
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
