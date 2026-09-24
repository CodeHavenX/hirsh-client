package com.cramsan.hirsh.ui.screens.patientedit

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.ui.components.AllergyActions
import com.cramsan.hirsh.ui.components.AllergyDraft
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewPatient = Patient(
    id = "07c98942-3654-4034-b960-f3265814e214",
    medicalRecordNumber = "HC-00142",
    documentType = DocumentType.NID,
    documentNumber = "45678901",
    firstName = "Maria",
    lastName = "Gonzalez",
    secondLastName = "Huerta",
    fullName = "Maria Gonzalez Huerta",
    birthDate = "14/03/1989",
    phone = "987-654-321",
    bloodType = "O+",
    allergies = listOf(
        Allergy("a1", AllergyType.MEDICATION, "Penicilina", Severity.SEVERE, "Urticaria generalizada tras la primera dosis, 2019."),
        Allergy("a2", AllergyType.FOOD, "Mariscos", severity = null),
    ),
    sex = Sex.FEMALE,
)

private val previewUiState = EditPatientUiState(
    isLoading = false,
    patient = previewPatient,
    firstName = previewPatient.firstName,
    lastName = previewPatient.lastName,
    secondLastName = previewPatient.secondLastName,
    documentNumber = previewPatient.documentNumber,
    birthDate = previewPatient.birthDate,
    phone = previewPatient.phone,
    sex = previewPatient.sex,
    bloodType = previewPatient.bloodType,
    allergies = previewPatient.allergies,
)

@PreviewResponsive
@Composable
private fun EditPatientScreenPreview() {
    HirshTheme {
        EditPatientScreenContent(
            uiState = previewUiState,
            patientId = previewPatient.id,
            onCancel = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onSecondLastNameChange = {},
            onPhoneChange = {},
            onBloodTypeChange = {},
            onSave = {},
            allergyActions = AllergyActions(),
        )
    }
}

@PreviewResponsive
@Composable
private fun EditPatientScreenAddingAllergyPreview() {
    HirshTheme {
        EditPatientScreenContent(
            uiState = previewUiState.copy(
                allergyDraft = AllergyDraft(allergyType = AllergyType.MEDICATION, description = "Ibuprofeno"),
            ),
            patientId = previewPatient.id,
            onCancel = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onSecondLastNameChange = {},
            onPhoneChange = {},
            onBloodTypeChange = {},
            onSave = {},
            allergyActions = AllergyActions(),
        )
    }
}
