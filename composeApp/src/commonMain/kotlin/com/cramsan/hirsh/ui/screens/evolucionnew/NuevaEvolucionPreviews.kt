package com.cramsan.hirsh.ui.screens.evolucionnew

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PlaceholderCatalog
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme
import com.cramsan.hirsh.util.formatDate
import com.cramsan.hirsh.util.formatTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    currentService = PlaceholderCatalog.service("Emergencia - Topico de Medicina"),
    currentBed = PlaceholderCatalog.bed("0"),
    attendingPhysician = PlaceholderCatalog.physician("Dr. Hirsh"),
    startDate = Instant.parse("2026-06-15T12:50:00Z"),
    endDate = null,
    admissionReason = "Sintomas respiratorios",
    status = EpisodeStatus.ACTIVE,
    historiaClinica = HistoriaClinica(),
    evoluciones = emptyList(),
)

private val previewOpenedNow = Instant.parse("2026-06-18T22:30:00Z").toLocalDateTime(TimeZone.currentSystemDefault())

private fun previewUiState(
    selectedTab: EvolucionTab = EvolucionTab.EVOLUCION,
) = NuevaEvolucionUiState(
    isLoading = false,
    patient = previewPatient,
    hospitalizacion = previewHospitalizacion,
    selectedTab = selectedTab,
    openedFecha = formatDate(previewOpenedNow.date),
    openedHora = formatTime(previewOpenedNow.time),
)

@PreviewResponsive
@Composable
private fun NuevaEvolucionScreenPreview() {
    HirshTheme {
        NuevaEvolucionScreenContent(
            uiState = previewUiState(),
            patientId = previewPatient.id,
            hospId = previewHospitalizacion.id,
            sessionDisplayName = "Dr. A. Patel",
            showDiscardDialog = false,
            onClose = {},
            onRequestDiscard = {},
            onDismissDiscardDialog = {},
            onConfirmDiscard = {},
            onSave = {},
            onSelectTab = {},
            onSubjectiveChange = {},
            onObjectiveChange = {},
            onAssessmentChange = {},
            onPlanChange = {},
            onRxChange = {},
            onPaChange = {},
            onFcChange = {},
            onFrChange = {},
            onTempChange = {},
            onSatO2Change = {},
            onFio2Change = {},
            onPronosticoChange = {},
            onResultadoEvolucionChange = {},
            onDxCodigoChange = { _, _ -> },
            onDxDescripcionChange = { _, _ -> },
            onRemoveDxRow = {},
            onAddDxRow = {},
            onExamTipoChange = { _, _ -> },
            onExamNombreChange = { _, _ -> },
            onExamResultadoChange = { _, _ -> },
            onExamUnidadChange = { _, _ -> },
            onExamReferenciaChange = { _, _ -> },
            onExamFechaChange = { _, _ -> },
            onRemoveExamRow = {},
            onAddExamRow = {},
            onExamenesObsChange = {},
        )
    }
}

/** Two diagnosis rows and one filled exam row -- exercises the Examenes tab's count badge. */
@PreviewResponsive
@Composable
private fun NuevaEvolucionScreenFilledPreview() {
    val uiState = previewUiState().copy(
        subjective = "Paciente refiere notable mejoria de disnea.",
        objective = "PA 115/72, FC 80, FR 20, SatO2 95% con FiO2 24%.",
        diagnosticos = listOf(
            DxRow(descripcion = "Neumonia por Pneumocystis"),
            DxRow(descripcion = "VIH Estadio SIDA"),
        ),
        pronostico = "Favorable",
        resultadoEvolucion = "Favorable",
        examenes = listOf(ExamRow(nombre = "Gasometria arterial - pO2")),
    )
    HirshTheme {
        NuevaEvolucionScreenContent(
            uiState = uiState,
            patientId = previewPatient.id,
            hospId = previewHospitalizacion.id,
            sessionDisplayName = "Dr. A. Patel",
            showDiscardDialog = false,
            onClose = {},
            onRequestDiscard = {},
            onDismissDiscardDialog = {},
            onConfirmDiscard = {},
            onSave = {},
            onSelectTab = {},
            onSubjectiveChange = {},
            onObjectiveChange = {},
            onAssessmentChange = {},
            onPlanChange = {},
            onRxChange = {},
            onPaChange = {},
            onFcChange = {},
            onFrChange = {},
            onTempChange = {},
            onSatO2Change = {},
            onFio2Change = {},
            onPronosticoChange = {},
            onResultadoEvolucionChange = {},
            onDxCodigoChange = { _, _ -> },
            onDxDescripcionChange = { _, _ -> },
            onRemoveDxRow = {},
            onAddDxRow = {},
            onExamTipoChange = { _, _ -> },
            onExamNombreChange = { _, _ -> },
            onExamResultadoChange = { _, _ -> },
            onExamUnidadChange = { _, _ -> },
            onExamReferenciaChange = { _, _ -> },
            onExamFechaChange = { _, _ -> },
            onRemoveExamRow = {},
            onAddExamRow = {},
            onExamenesObsChange = {},
        )
    }
}

/** Examenes tab selected. */
@PreviewResponsive
@Composable
private fun NuevaEvolucionScreenExamenesTabPreview() {
    val uiState = previewUiState(selectedTab = EvolucionTab.EXAMENES).copy(
        examenes = listOf(ExamRow(nombre = "Hemoglobina")),
    )
    HirshTheme {
        NuevaEvolucionScreenContent(
            uiState = uiState,
            patientId = previewPatient.id,
            hospId = previewHospitalizacion.id,
            sessionDisplayName = "Dr. A. Patel",
            showDiscardDialog = false,
            onClose = {},
            onRequestDiscard = {},
            onDismissDiscardDialog = {},
            onConfirmDiscard = {},
            onSave = {},
            onSelectTab = {},
            onSubjectiveChange = {},
            onObjectiveChange = {},
            onAssessmentChange = {},
            onPlanChange = {},
            onRxChange = {},
            onPaChange = {},
            onFcChange = {},
            onFrChange = {},
            onTempChange = {},
            onSatO2Change = {},
            onFio2Change = {},
            onPronosticoChange = {},
            onResultadoEvolucionChange = {},
            onDxCodigoChange = { _, _ -> },
            onDxDescripcionChange = { _, _ -> },
            onRemoveDxRow = {},
            onAddDxRow = {},
            onExamTipoChange = { _, _ -> },
            onExamNombreChange = { _, _ -> },
            onExamResultadoChange = { _, _ -> },
            onExamUnidadChange = { _, _ -> },
            onExamReferenciaChange = { _, _ -> },
            onExamFechaChange = { _, _ -> },
            onRemoveExamRow = {},
            onAddExamRow = {},
            onExamenesObsChange = {},
        )
    }
}
