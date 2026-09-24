package com.cramsan.hirsh.ui.screens.patientlist

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewPatients = listOf(
    Patient(
        id = "07c98942-3654-4034-b960-f3265814e214",
        medicalRecordNumber = "HC-00142",
        documentType = DocumentType.NID,
        documentNumber = "45678901",
        fullName = "Maria Gonzalez Huerta",
        birthDate = "14/03/1989",
        phone = "987-654-321",
        bloodType = "O+",
        allergies = listOf(Allergy("allergy_Penicilina", AllergyType.OTHER, "Penicilina", null)),
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
