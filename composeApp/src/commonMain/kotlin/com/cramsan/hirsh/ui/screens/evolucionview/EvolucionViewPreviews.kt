package com.cramsan.hirsh.ui.screens.evolucionview

import com.cramsan.hirsh.model.Prescripcion
import com.cramsan.hirsh.model.RouteOfAdministration
import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Examen
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.repository.PlaceholderCatalog
import com.cramsan.hirsh.ui.preview.PreviewComponent
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

private fun previewEvolucion(examenes: List<Examen>, status: NoteStatus = NoteStatus.SIGNED) = Evolucion(
    id = "v5c",
    fecha = "18 Jun 2026",
    hora = "22:30",
    medico = "Dr. Hirsh",
    vitals = Vitals(pa = "115/72", fc = "80", fr = "20", temp = "37.0", satO2 = "95", fio2 = "24"),
    diagnosticos = listOf(
        DiagnosticoCie10("B59", "Neumonia por Pneumocystis"),
        DiagnosticoCie10("B24", "VIH Estadio SIDA"),
    ),
    subjective = "Paciente refiere notable mejoria de disnea. Tolera destete progresivo de oxigeno.",
    objective = "PA 115/72, FC 80, FR 20, SatO2 95% con FiO2 24%. MV mejor ventilado bilateral.",
    assessment = "Buena respuesta a tratamiento antimicrobiano. Resultado de BK pendiente aun.",
    plan = "Continuar TMP-SMX y corticoides en descenso. Reevaluar destete de O2.",
    rx = "O2 por CBN 1L",
    prescripciones = listOf(
        Prescripcion("d1", "Trimetoprima/sulfametoxazol 800/160 mg · Ampolla", "15 mg/kg/dia", "Cada 8 h", RouteOfAdministration.IV, "21 dias", ""),
        Prescripcion("d2", "Prednisona 20 mg · Tableta", "40 mg", "Cada 12 h", RouteOfAdministration.PO, "", "En descenso"),
    ),
    pronostico = Pronostico.FAVORABLE,
    resultado = EvolucionResultado.FAVORABLE,
    examenes = examenes,
    examenesObs = if (examenes.isEmpty()) "" else "Mejoria gasometrica respecto al ingreso. BK de esputo aun pendiente.",
    status = status,
)

private fun previewHospitalizacion(evolucion: Evolucion) = Hospitalizacion(
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

private val previewExamenes = listOf(
    Examen(
        tipo = "Laboratorio",
        nombre = "Gasometria arterial - pO2",
        resultado = "76",
        unidad = "mmHg",
        referencia = "80 - 100",
        fecha = "18 Jun 2026",
    ),
    Examen(
        tipo = "Laboratorio",
        nombre = "BK en esputo (seriado)",
        resultado = "Pendiente",
        unidad = "—",
        referencia = "Negativo",
        fecha = "18 Jun 2026",
    ),
)

private fun previewUiState(
    hospitalizacion: Hospitalizacion,
    evolucion: Evolucion,
    selectedTab: EvolucionViewTab = EvolucionViewTab.EVOLUCION,
    canSign: Boolean = false,
    error: String? = null,
    showCopiedConfirmation: Boolean = false,
    canApplyScale: Boolean = true,
) = EvolucionViewUiState(
    isLoading = false,
    patient = previewPatient,
    hospitalizacion = hospitalizacion,
    evolucion = evolucion,
    selectedTab = selectedTab,
    canSign = canSign,
    error = error,
    sisText = evolucionSisText(previewPatient, evolucion),
    showCopiedConfirmation = showCopiedConfirmation,
    canApplyScale = canApplyScale,
)

@PreviewResponsive
@Composable
private fun EvolucionViewScreenPreview() {
    val evolucion = previewEvolucion(previewExamenes)
    val hospitalizacion = previewHospitalizacion(evolucion)
    HirshTheme {
        EvolucionViewScreenContent(
            uiState = previewUiState(hospitalizacion, evolucion),
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectTab = {},
            onRetry = {},
            onSign = {},
            onCopiedForSis = {},
            onCopyForSisFailed = {},
            onApplyScale = {},
        )
    }
}

/** No examenes recorded -- exercises the Examenes tab's empty-state box. */
@PreviewResponsive
@Composable
private fun EvolucionViewScreenNoExamenesPreview() {
    val evolucion = previewEvolucion(emptyList())
    val hospitalizacion = previewHospitalizacion(evolucion)
    HirshTheme {
        EvolucionViewScreenContent(
            uiState = previewUiState(hospitalizacion, evolucion),
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectTab = {},
            onRetry = {},
            onSign = {},
            onCopiedForSis = {},
            onCopyForSisFailed = {},
            onApplyScale = {},
        )
    }
}

/** Examenes tab selected. */
@PreviewResponsive
@Composable
private fun EvolucionViewScreenExamenesTabPreview() {
    val evolucion = previewEvolucion(previewExamenes)
    val hospitalizacion = previewHospitalizacion(evolucion)
    HirshTheme {
        EvolucionViewScreenContent(
            uiState = previewUiState(hospitalizacion, evolucion, selectedTab = EvolucionViewTab.EXAMENES),
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectTab = {},
            onRetry = {},
            onSign = {},
            onCopiedForSis = {},
            onCopyForSisFailed = {},
            onApplyScale = {},
        )
    }
}

/** A draft awaiting a signer, seen by someone who can sign it, after a refused signature. */
@PreviewResponsive
@Composable
private fun EvolucionViewScreenDraftPreview() {
    val evolucion = previewEvolucion(previewExamenes, status = NoteStatus.DRAFT)
    val hospitalizacion = previewHospitalizacion(evolucion)
    HirshTheme {
        EvolucionViewScreenContent(
            uiState = previewUiState(hospitalizacion, evolucion, canSign = true, error = EVO_SIGN_FORBIDDEN_ERROR),
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectTab = {},
            onRetry = {},
            onSign = {},
            onCopiedForSis = {},
            onCopyForSisFailed = {},
            onApplyScale = {},
        )
    }
}

/** Right after "Copiar texto limpio para SIS", while the confirmation shows. */
@PreviewResponsive
@Composable
private fun EvolucionViewScreenCopiedPreview() {
    val evolucion = previewEvolucion(previewExamenes)
    val hospitalizacion = previewHospitalizacion(evolucion)
    HirshTheme {
        EvolucionViewScreenContent(
            uiState = previewUiState(hospitalizacion, evolucion, showCopiedConfirmation = true),
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectTab = {},
            onRetry = {},
            onSign = {},
            onCopiedForSis = {},
            onCopyForSisFailed = {},
            onApplyScale = {},
        )
    }
}

/** HISS-501's print-preview summary, standalone (not behind the Screen's Dialog) so Roborazzi can capture it. */
@PreviewComponent
@Composable
private fun EvolucionPrintablePreview() {
    val evolucion = previewEvolucion(previewExamenes)
    val hospitalizacion = previewHospitalizacion(evolucion)
    HirshTheme {
        EvolucionPrintable(
            patient = previewPatient,
            hospitalizacion = hospitalizacion,
            evolucion = evolucion,
            onBack = {},
        )
    }
}
