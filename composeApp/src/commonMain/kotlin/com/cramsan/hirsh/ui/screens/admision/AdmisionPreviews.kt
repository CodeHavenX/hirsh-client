package com.cramsan.hirsh.ui.screens.admision

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
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

private val previewServices = listOf(
    ServiceRef("svc-1", "Emergencia - Topico de Medicina"),
    ServiceRef("svc-2", "Medicina General"),
    ServiceRef("svc-3", "Psiquiatria (UHSMA)"),
)

private val previewPhysicians = listOf(
    UserRef("usr-1", "Anita Patel"),
    UserRef("usr-2", "Marco Reyes"),
)

private val previewForm = AdmisionUiState(
    isLoading = false,
    patient = previewPatient,
    services = previewServices,
    physicians = previewPhysicians,
    service = previewServices[2],
    beds = listOf(BedRef("bed-1", "03"), BedRef("bed-2", "07"), BedRef("bed-3", "12")),
)

@Composable
private fun AdmisionPreview(uiState: AdmisionUiState) {
    HirshTheme {
        AdmisionScreenContent(
            uiState = uiState,
            onCancel = {},
            onServicioChange = {},
            onCamaChange = {},
            onMedicoResponsableChange = {},
            onMotivoChange = {},
            onRegister = {},
        )
    }
}

/** A service picked, so its available beds are offered; bed and physician still to choose. */
@PreviewResponsive
@Composable
private fun AdmisionScreenPreview() = AdmisionPreview(previewForm)

@PreviewResponsive
@Composable
private fun AdmisionActiveStayPreview() = AdmisionPreview(previewForm.copy(hasActiveHospitalization = true))
