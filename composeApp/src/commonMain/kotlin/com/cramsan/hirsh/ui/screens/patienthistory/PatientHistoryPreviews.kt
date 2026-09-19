package com.cramsan.hirsh.ui.screens.patienthistory

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.FieldChange
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
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

private val previewChangeLog = mapOf(
    "07c98942-3654-4034-b960-f3265814e214" to listOf(
        PatientChangeLogEntry(
            changedBy = "apatel",
            fecha = "05 May 2026",
            hora = "11:20",
            fields = listOf(FieldChange("allergies", "Alergias conocidas", "Ninguna", "Penicilina")),
        ),
        PatientChangeLogEntry(
            changedBy = "mreyes",
            fecha = "13 Abr 2026",
            hora = "08:30",
            fields = listOf(FieldChange("phone", "Telefono de contacto", "987-654-320", "987-654-321")),
        ),
    ),
)

private fun previewUiState(patientId: String) = PatientHistoryUiState(
    isLoading = false,
    patient = previewPatients.find { it.id == patientId },
    rows = previewChangeLog[patientId].orEmpty().flatMap { entry ->
        entry.fields.map { field ->
            ChangeHistoryRow(
                fecha = entry.fecha,
                hora = entry.hora,
                changedBy = entry.changedBy,
                label = field.label,
                oldValue = field.oldValue,
                newValue = field.newValue,
            )
        }
    },
)

@PreviewResponsive
@Composable
private fun PatientHistoryScreenPreview() {
    HirshTheme {
        PatientHistoryScreenContent(
            uiState = previewUiState("07c98942-3654-4034-b960-f3265814e214"),
            patientId = "07c98942-3654-4034-b960-f3265814e214",
            onBack = {},
        )
    }
}

/** 1e0697d0-f3e4-4755-85ad-d888d2b15f9d has no change-log entries in this preview's fixtures -- exercises the empty state. */
@PreviewResponsive
@Composable
private fun PatientHistoryScreenEmptyPreview() {
    HirshTheme {
        PatientHistoryScreenContent(
            uiState = previewUiState("1e0697d0-f3e4-4755-85ad-d888d2b15f9d"),
            patientId = "1e0697d0-f3e4-4755-85ad-d888d2b15f9d",
            onBack = {},
        )
    }
}
