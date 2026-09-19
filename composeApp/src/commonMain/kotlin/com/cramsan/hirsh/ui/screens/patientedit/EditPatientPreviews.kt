package com.cramsan.hirsh.ui.screens.patientedit

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.singleAllergyFromText
import com.cramsan.hirsh.model.summary
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewPatient = Patient(
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
)

private val previewUiState = EditPatientUiState(
    isLoading = false,
    patient = previewPatient,
    fullName = previewPatient.fullName,
    documentNumber = previewPatient.documentNumber,
    birthDate = previewPatient.birthDate,
    phone = previewPatient.phone,
    sex = previewPatient.sex,
    bloodType = previewPatient.bloodType,
    allergies = previewPatient.allergies.summary(),
)

@PreviewResponsive
@Composable
private fun EditPatientScreenPreview() {
    HirshTheme {
        EditPatientScreenContent(
            uiState = previewUiState,
            patientId = previewPatient.id,
            onCancel = {},
            onNameChange = {},
            onNationalIdChange = {},
            onDateOfBirthChange = {},
            onPhoneChange = {},
            onSexChange = {},
            onBloodTypeChange = {},
            onAllergiesChange = {},
            onSave = {},
        )
    }
}
