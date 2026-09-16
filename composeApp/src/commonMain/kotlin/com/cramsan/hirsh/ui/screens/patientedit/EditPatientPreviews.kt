package com.cramsan.hirsh.ui.screens.patientedit

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewPatient = Patient(
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
)

private val previewUiState = EditPatientUiState(
    isLoading = false,
    patient = previewPatient,
    name = previewPatient.name,
    nationalId = previewPatient.nationalId,
    dateOfBirth = previewPatient.dateOfBirth,
    phone = previewPatient.phone,
    sex = previewPatient.sex,
    bloodType = previewPatient.bloodType,
    allergies = previewPatient.allergies,
    assignedDoctor = previewPatient.assignedDoctor,
)

@Preview
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
            onAssignedDoctorChange = {},
            onSave = {},
        )
    }
}
