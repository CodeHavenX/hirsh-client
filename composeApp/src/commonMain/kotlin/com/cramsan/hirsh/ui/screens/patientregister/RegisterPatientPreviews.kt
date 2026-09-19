package com.cramsan.hirsh.ui.screens.patientregister

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.singleAllergyFromText
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
        allergies = singleAllergyFromText("Penicilina"),
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
                fullName = "Maria Gonzalez",
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
            onRegister = {},
        )
    }
}
