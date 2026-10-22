package com.cramsan.hirsh.ui.screens.discharge

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.DischargeCondition
import com.cramsan.hirsh.model.DischargeType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Icd10Code
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PlaceholderCatalog
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme
import kotlin.time.Instant

private val previewPatient = Patient(
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
)

private val previewHospitalizacion = Hospitalizacion(
    id = "h_mendoza_1",
    patientId = previewPatient.id,
    currentService = PlaceholderCatalog.service("Psiquiatria (UHSMA)"),
    currentBed = PlaceholderCatalog.bed("12"),
    attendingPhysician = PlaceholderCatalog.physician("Dr. Hirsh"),
    startDate = Instant.parse("2026-06-15T12:50:00Z"),
    endDate = null,
    admissionReason = "Ideacion suicida",
    status = EpisodeStatus.ACTIVE,
)

private val depressionCodes = listOf(
    Icd10Code("F32.2", "Episodio depresivo grave sin síntomas psicóticos"),
    Icd10Code("F32.3", "Episodio depresivo grave con síntomas psicóticos"),
    Icd10Code("F33.2", "Trastorno depresivo recurrente, episodio depresivo grave presente sin síntomas psicóticos"),
)

@Composable
private fun Preview(uiState: DischargeUiState) {
    HirshTheme {
        DischargeScreenContent(
            uiState = uiState,
            onCancel = {},
            onTypeChange = {},
            onConditionChange = {},
            onDiagnosisQueryChange = {},
            onDiagnosisSelected = {},
            onDiagnosisCleared = {},
            onNextAppointmentDateChange = {},
            onFollowUpPlanChange = {},
            onDischarge = {},
            onRetry = {},
        )
    }
}

private val loadedState = DischargeUiState(isLoading = false, patient = previewPatient, hospitalizacion = previewHospitalizacion)

/** Empty form, as first opened. */
@PreviewResponsive
@Composable
private fun DischargeScreenEmptyPreview() {
    Preview(loadedState)
}

/** Mid-search: typeahead results listed below the diagnosis field. */
@PreviewResponsive
@Composable
private fun DischargeScreenSearchingPreview() {
    Preview(
        loadedState.copy(
            type = DischargeType.MEDICAL,
            condition = DischargeCondition.IMPROVED,
            diagnosisQuery = "depresivo grave",
            diagnosisResults = depressionCodes,
        ),
    )
}

/** Every field filled, with a picked diagnosis and a server error under the form. */
@PreviewResponsive
@Composable
private fun DischargeScreenFilledPreview() {
    Preview(
        loadedState.copy(
            type = DischargeType.MEDICAL,
            condition = DischargeCondition.IMPROVED,
            diagnosis = depressionCodes.first(),
            nextAppointmentDate = "17/07/2026",
            followUpPlan = "Control ambulatorio en 15 dias. Continuar sertralina 100 mg/dia.",
            error = "No se pudo dar de alta al paciente",
        ),
    )
}
