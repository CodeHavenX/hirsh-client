package com.cramsan.hirsh.ui.screens.evolucionnew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Drug
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.RouteOfAdministration
import com.cramsan.hirsh.model.Severity
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
            onRetry = {},
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
            onRetry = {},
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
            onRetry = {},
        )
    }
}

private fun previewDrug(id: String, name: String, concentration: String, form: String, group: String) =
    Drug(id, name, concentration, form, RouteOfAdministration.PO, group, isPsychotropic = true)

private val previewSertralina = previewDrug("d1", "Sertralina", "50 mg", "Tableta", "Antidepresivos ISRS")
private val previewValproico = previewDrug("d2", "Ácido valproico", "500 mg", "Tableta", "Estabilizadores del ánimo")

/**
 * The prescriptions alone, since they sit below the fold of the full form: one still searching the
 * formulary, a complete one, and one whose drug matches a recorded medication allergy.
 */
@PreviewResponsive
@Composable
private fun NuevaEvolucionPrescriptionsPreview() {
    val rows = listOf(
        RxRow(query = "quet"),
        RxRow(drug = previewSertralina, dose = "50 mg", frequency = "Cada 24 h", route = RouteOfAdministration.PO, duration = "30 dias"),
        RxRow(drug = previewValproico, dose = "500 mg", frequency = "Cada 12 h", route = RouteOfAdministration.PO),
    )
    val allergic = previewPatient.copy(
        allergies = listOf(Allergy("a1", AllergyType.MEDICATION, "Acido valproico", Severity.SEVERE)),
    )
    val uiState = previewUiState().copy(
        patient = allergic,
        prescripciones = rows,
        drugSearch = DrugSearch(
            row = 0,
            query = "quet",
            results = listOf(previewDrug("d3", "Quetiapina", "100 mg", "Tableta", "Antipsicóticos atípicos")),
        ),
    )
    HirshTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(24.dp)) {
                PrescriptionRows(
                    rows = uiState.prescripciones,
                    allergies = uiState.rxAllergies,
                    search = uiState.drugSearch,
                    callbacks = PrescriptionCallbacks(),
                )
            }
        }
    }
}
