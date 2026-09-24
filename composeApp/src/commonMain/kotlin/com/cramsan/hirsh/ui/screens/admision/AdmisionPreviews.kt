package com.cramsan.hirsh.ui.screens.admision

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewPatient = Patient(
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
