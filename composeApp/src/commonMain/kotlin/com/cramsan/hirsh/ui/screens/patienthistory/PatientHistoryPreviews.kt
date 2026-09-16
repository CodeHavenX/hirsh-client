package com.cramsan.hirsh.ui.screens.patienthistory

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.FieldChange
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.ui.preview.Preview
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
    Patient(
        id = "#00135",
        name = "Jesus Alberto Mendoza Aguilar",
        dateOfBirth = "10/08/1990",
        phone = "955-123-456",
        assignedDoctor = "Dr. Patel",
        lastVisit = "18 Jun 2026",
        bloodType = "B+",
        allergies = "Ninguna",
        nationalId = "70567572",
        sex = Sex.MALE,
    ),
)

private val previewChangeLog = mapOf(
    "#00142" to listOf(
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

@Preview
@Composable
private fun PatientHistoryScreenPreview() {
    HirshTheme {
        PatientHistoryScreenContent(
            uiState = previewUiState("#00142"),
            patientId = "#00142",
            onBack = {},
        )
    }
}

/** #00135 has no change-log entries in this preview's fixtures -- exercises the empty state. */
@Preview
@Composable
private fun PatientHistoryScreenEmptyPreview() {
    HirshTheme {
        PatientHistoryScreenContent(
            uiState = previewUiState("#00135"),
            patientId = "#00135",
            onBack = {},
        )
    }
}
