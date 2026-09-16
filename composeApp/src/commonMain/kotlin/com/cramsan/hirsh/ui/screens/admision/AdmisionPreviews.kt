package com.cramsan.hirsh.ui.screens.admision

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.ui.preview.PreviewResponsive
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

@PreviewResponsive
@Composable
private fun AdmisionScreenPreview() {
    HirshTheme {
        AdmisionScreenContent(
            uiState = AdmisionUiState(isLoading = false, patient = previewPatient),
            patientId = previewPatient.id,
            onCancel = {},
            onServicioChange = {},
            onCamaChange = {},
            onMedicoResponsableChange = {},
            onMotivoChange = {},
            onRegister = {},
        )
    }
}
