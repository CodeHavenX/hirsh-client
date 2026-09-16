package com.cramsan.hirsh.ui.screens.patientregister

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.ui.preview.PreviewResponsive
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
)

@PreviewResponsive
@Composable
private fun RegisterPatientScreenPreview() {
    HirshTheme {
        RegisterPatientScreenContent(
            uiState = RegisterPatientUiState(),
            onCancel = {},
            onViewExistingPatient = {},
            onNameChange = {},
            onNationalIdChange = {},
            onDateOfBirthChange = {},
            onPhoneChange = {},
            onSexChange = {},
            onCheckDuplicate = {},
            onBloodTypeChange = {},
            onAllergiesChange = {},
            onAssignedDoctorChange = {},
            onRegister = {},
        )
    }
}

@PreviewResponsive
@Composable
private fun RegisterPatientScreenDuplicateWarningPreview() {
    HirshTheme {
        RegisterPatientScreenContent(
            uiState = RegisterPatientUiState(
                name = "Maria Gonzalez",
                duplicateWarning = previewPatients.first(),
            ),
            onCancel = {},
            onViewExistingPatient = {},
            onNameChange = {},
            onNationalIdChange = {},
            onDateOfBirthChange = {},
            onPhoneChange = {},
            onSexChange = {},
            onCheckDuplicate = {},
            onBloodTypeChange = {},
            onAllergiesChange = {},
            onAssignedDoctorChange = {},
            onRegister = {},
        )
    }
}
