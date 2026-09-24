package com.cramsan.hirsh.ui.screens.patientregister

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

private val localAllergies = listOf(
    Allergy("local_1", AllergyType.MEDICATION, "Penicilina", Severity.SEVERE, "Rash generalizado"),
    Allergy("local_2", AllergyType.FOOD, "Mariscos", null),
)

private val filledForm = RegisterPatientUiState(
    medicalRecordNumber = "HC-2026-004312",
    firstName = "Luis",
    lastName = "Quispe",
    secondLastName = "Mamani",
    documentNumber = "45821337",
    birthDate = "12/04/1991",
    phone = "555-0100",
    sex = Sex.MALE,
    bloodType = "O+",
)

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
        allergies = listOf(Allergy("a1", AllergyType.MEDICATION, "Penicilina", Severity.SEVERE)),
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
            onMedicalRecordNumberChange = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onSecondLastNameChange = {},
            onNationalIdChange = {},
            onDateOfBirthChange = {},
            onPhoneChange = {},
            onSexChange = {},
            onCheckDuplicate = {},
            onBloodTypeChange = {},
            allergyActions = AllergyActions(),
            onRegister = {},
            onRetryAllergies = {},
            onGoToRecord = {},
        )
    }
}

@PreviewResponsive
@Composable
private fun RegisterPatientScreenDuplicateWarningPreview() {
    HirshTheme {
        RegisterPatientScreenContent(
            uiState = RegisterPatientUiState(
                firstName = "Maria",
                lastName = "Gonzalez",
                duplicateWarning = previewPatients.first(),
            ),
            onCancel = {},
            onViewExistingPatient = {},
            onMedicalRecordNumberChange = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onSecondLastNameChange = {},
            onNationalIdChange = {},
            onDateOfBirthChange = {},
            onPhoneChange = {},
            onSexChange = {},
            onCheckDuplicate = {},
            onBloodTypeChange = {},
            allergyActions = AllergyActions(),
            onRegister = {},
            onRetryAllergies = {},
            onGoToRecord = {},
        )
    }
}

@PreviewResponsive
@Composable
private fun RegisterPatientScreenAllergiesPreview() {
    HirshTheme {
        RegisterPatientScreenContent(
            uiState = filledForm.copy(
                allergies = localAllergies,
                allergyDraft = AllergyDraft(allergyType = AllergyType.ENVIRONMENTAL, description = "Polen"),
            ),
            onCancel = {},
            onViewExistingPatient = {},
            onMedicalRecordNumberChange = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onSecondLastNameChange = {},
            onNationalIdChange = {},
            onDateOfBirthChange = {},
            onPhoneChange = {},
            onSexChange = {},
            onCheckDuplicate = {},
            onBloodTypeChange = {},
            allergyActions = AllergyActions(),
            onRegister = {},
            onRetryAllergies = {},
            onGoToRecord = {},
        )
    }
}

/** The patient was created but one allergy didn't save: the form is locked to Reintentar / Ir al registro. */
@PreviewResponsive
@Composable
private fun RegisterPatientScreenAllergySaveFailedPreview() {
    HirshTheme {
        RegisterPatientScreenContent(
            uiState = filledForm.copy(
                allergies = localAllergies.take(1),
                createdPatientId = "new-patient-id",
                error = "Paciente registrado, pero no se pudieron guardar 1 alergia(s)",
            ),
            onCancel = {},
            onViewExistingPatient = {},
            onMedicalRecordNumberChange = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onSecondLastNameChange = {},
            onNationalIdChange = {},
            onDateOfBirthChange = {},
            onPhoneChange = {},
            onSexChange = {},
            onCheckDuplicate = {},
            onBloodTypeChange = {},
            allergyActions = AllergyActions(),
            onRegister = {},
            onRetryAllergies = {},
            onGoToRecord = {},
        )
    }
}
