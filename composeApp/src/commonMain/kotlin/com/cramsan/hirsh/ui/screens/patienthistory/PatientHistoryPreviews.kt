package com.cramsan.hirsh.ui.screens.patienthistory

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.AccessAction
import com.cramsan.hirsh.model.AccessLogEntry
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
        id = "1e0697d0-f3e4-4755-85ad-d888d2b15f9d",
        medicalRecordNumber = "HC-00135",
        documentType = DocumentType.NID,
        documentNumber = "70567572",
        fullName = "Jesus Alberto Mendoza Aguilar",
        birthDate = "10/08/1990",
        phone = "955-123-456",
        bloodType = "B+",
        allergies = emptyList(),
        sex = Sex.MALE,
    ),
)

private val previewEntries = listOf(
    AccessLogEntry("e1", "3f2504e0-4f89-41d3-9a0c-0305e82c3301", AccessAction.RECORD_READ, "24 Sep 2026 · 11:05", "10.20.30.41"),
    AccessLogEntry("e2", "3f2504e0-4f89-41d3-9a0c-0305e82c3301", AccessAction.PATIENT_MODIFIED, "24 Sep 2026 · 11:02", "10.20.30.41"),
    AccessLogEntry("e4", "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d", AccessAction.PRINT, "23 Sep 2026 · 18:42", "10.20.30.57"),
    AccessLogEntry("e3", "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d", AccessAction.RECORD_READ, "23 Sep 2026 · 18:40", "10.20.30.57"),
)

@PreviewResponsive
@Composable
private fun PatientHistoryScreenPreview() {
    HirshTheme {
        PatientHistoryScreenContent(
            uiState = PatientHistoryUiState(
                isLoading = false,
                patient = previewPatients[0],
                entries = previewEntries,
                totalEntries = 132,
            ),
            onBack = {},
        )
    }
}

/** A patient whose record nobody has opened yet -- exercises the empty state. */
@PreviewResponsive
@Composable
private fun PatientHistoryScreenEmptyPreview() {
    HirshTheme {
        PatientHistoryScreenContent(
            uiState = PatientHistoryUiState(isLoading = false, patient = previewPatients[1]),
            onBack = {},
        )
    }
}

/** A session without AUDIT_READ that reaches this screen anyway gets a 403 from the backend. */
@PreviewResponsive
@Composable
private fun PatientHistoryScreenForbiddenPreview() {
    HirshTheme {
        PatientHistoryScreenContent(
            uiState = PatientHistoryUiState(isLoading = false, patient = previewPatients[0], error = AccessLogError.FORBIDDEN),
            onBack = {},
        )
    }
}
