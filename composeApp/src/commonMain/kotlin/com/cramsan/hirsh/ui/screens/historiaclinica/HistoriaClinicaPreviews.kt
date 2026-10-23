package com.cramsan.hirsh.ui.screens.historiaclinica

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.ExamenRegional
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PlaceholderCatalog
import com.cramsan.hirsh.ui.preview.PreviewComponent
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme
import kotlin.time.Instant

private val previewPatient = Patient(
    id = "30c14d79-8c7e-43f8-870c-f1dbc4c90247",
    medicalRecordNumber = "HC-00129",
    documentType = DocumentType.NID,
    documentNumber = "70083906",
    fullName = "Karla Sofia Ricaldi Sedano",
    birthDate = "15/10/2012",
    phone = "998-984-134",
    bloodType = "—",
    allergies = emptyList(),
    sex = Sex.FEMALE,
)

private fun previewFiliacion() = Filiacion(
    edad = "13 anos",
    fechaNacimiento = "15/10/2012",
    estadoCivil = "Soltera",
    sexo = "Femenino",
    dni = "70083906",
    gradoInstruccion = "Secundaria Incompleta",
    ocupacion = "Estudiante",
    lugarNacimiento = "Hospital Edgardo Rebagliati Martins",
    lugarProcedencia = "Villa el Salvador",
    familiarResponsable = "Madre: Sandra Sedano Montalvo · 998984134",
    direccion = "Manzana I Lote 34, Villa el Salvador",
    servicioIngreso = "Emergencia de Pediatria, luego UHSMA",
)

private fun previewHistoriaClinica(partial: Boolean, status: NoteStatus = NoteStatus.DRAFT) = HistoriaClinica(
    status = status,
    filiacion = HcSection(complete = true, data = previewFiliacion()),
    motivoIngreso = HcSection(
        complete = true,
        data = MotivoIngreso(heteroagresividad = true, psicosis = true, adicciones = true),
    ),
    enfermedadActual = if (partial) {
        HcSection()
    } else {
        HcSection(
            complete = true,
            data = EnfermedadActual(
                tiempoEnfermedad = "2 anos",
                formaInicio = "Insidioso",
                curso = "Progresivo",
                duracionEpisodio = "4 dias",
                relato = "Episodio actual de heteroagresividad hacia los padres.",
            ),
        )
    },
    examenFisico = if (partial) {
        HcSection()
    } else {
        HcSection(
            complete = true,
            data = ExamenFisico(
                pa = "110/70",
                fc = "88",
                fr = "18",
                temp = "36.6",
                peso = "48",
                talla = "158",
                imc = "19.2",
                estadoGeneral = "Aparente regular estado general.",
                examenRegional = ExamenRegional(
                    cabezaCuello = "Normocefalo.",
                    toraxPulmones = "MV pasa bien.",
                    corazon = "RCR BI.",
                    abdomen = "Blando.",
                    neurologico = "Despierta.",
                ),
            ),
        )
    },
    diagnostico = if (partial) {
        HcSection()
    } else {
        HcSection(
            complete = true,
            data = Diagnostico(
                ejeI = "Psicosis aguda (F29.X)",
                ejeII = "—",
                ejeIII = "—",
                ejeIV = "Apoyo familiar inadecuado (Z63.2)",
                ejeV = "EEAG 78%",
            ),
        )
    },
    plan = if (partial) {
        HcSection()
    } else {
        HcSection(
            complete = true,
            data = Plan(
                lugarHospitalizacion = "UHSMA: Area de Damas",
                examenesSolicitados = "Examenes de laboratorio basales",
                psicofarmacos = "EV, VO, IM",
                evaluacionesSolicitadas = "Evaluacion por psicologia",
            ),
        )
    },
)

private val previewHospitalizacion = Hospitalizacion(
    id = "h_ricaldi_1",
    patientId = previewPatient.id,
    currentService = PlaceholderCatalog.service("Psiquiatria (UHSMA)"),
    currentBed = PlaceholderCatalog.bed("01"),
    attendingPhysician = PlaceholderCatalog.physician("Dr. Reyes"),
    startDate = Instant.parse("2026-05-20T14:30:00Z"),
    endDate = null,
    admissionReason = "Heteroagresividad, psicosis, adicciones",
    status = EpisodeStatus.ACTIVE,
)

private fun previewUiState(
    historiaClinica: HistoriaClinica,
    activeSection: HcSectionKey = HcSectionKey.FILIACION,
    canSign: Boolean = false,
    error: String? = null,
) = HistoriaClinicaUiState(
    isLoading = false,
    patient = previewPatient,
    hospitalizacion = previewHospitalizacion,
    historiaClinica = historiaClinica,
    activeSection = activeSection,
    fieldValues = fieldMapFor(activeSection, historiaClinica).orEmpty(),
    motivoIngresoDraft = historiaClinica.motivoIngreso.data ?: MotivoIngreso(),
    isReadOnly = historiaClinica.status == NoteStatus.SIGNED,
    canSign = canSign,
    error = error,
    canDownloadPdf = historiaClinica.status == NoteStatus.SIGNED,
)

@PreviewResponsive
@Composable
private fun HistoriaClinicaScreenFiliacionPreview() {
    val historiaClinica = previewHistoriaClinica(partial = false)
    HirshTheme {
        HistoriaClinicaScreenContent(
            uiState = previewUiState(historiaClinica, canSign = true),
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectSection = {},
            onMotivoOptionChange = { _, _, _ -> },
            onMotivoOtrosDetalleChange = { _, _ -> },
            onFieldChange = { _, _ -> },
            onSave = {},
            onSign = {},
            onRetry = {},
        )
    }
}

/**
 * Only Filiacion/Motivo/Enfermedad Actual complete -- exercises the rail's mixed done/pending
 * markers, and the message a save that lost to another user's edit leaves under the form.
 */
@PreviewResponsive
@Composable
private fun HistoriaClinicaScreenPartialPreview() {
    val historiaClinica = previewHistoriaClinica(partial = true)
    HirshTheme {
        HistoriaClinicaScreenContent(
            uiState = previewUiState(historiaClinica, error = SAVE_CONFLICT_ERROR),
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectSection = {},
            onMotivoOptionChange = { _, _, _ -> },
            onMotivoOtrosDetalleChange = { _, _ -> },
            onFieldChange = { _, _ -> },
            onSave = {},
            onSign = {},
            onRetry = {},
        )
    }
}

/** Motivo de Ingreso section active -- exercises the checkbox grid. */
@PreviewResponsive
@Composable
private fun HistoriaClinicaScreenMotivoIngresoPreview() {
    val historiaClinica = previewHistoriaClinica(partial = false)
    HirshTheme {
        HistoriaClinicaScreenContent(
            uiState = previewUiState(historiaClinica, activeSection = HcSectionKey.MOTIVO_INGRESO),
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectSection = {},
            onMotivoOptionChange = { _, _, _ -> },
            onMotivoOtrosDetalleChange = { _, _ -> },
            onFieldChange = { _, _ -> },
            onSave = {},
            onSign = {},
            onRetry = {},
        )
    }
}

/** Signed: the sections stay readable, but there is nothing left to save or sign. */
@PreviewResponsive
@Composable
private fun HistoriaClinicaScreenSignedPreview() {
    val historiaClinica = previewHistoriaClinica(partial = false, status = NoteStatus.SIGNED)
    HirshTheme {
        HistoriaClinicaScreenContent(
            uiState = previewUiState(historiaClinica),
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectSection = {},
            onMotivoOptionChange = { _, _, _ -> },
            onMotivoOtrosDetalleChange = { _, _ -> },
            onFieldChange = { _, _ -> },
            onSave = {},
            onSign = {},
            onRetry = {},
        )
    }
}

/** HISS-501's print-preview summary, standalone (not behind the Screen's Dialog) so Roborazzi can capture it. */
@PreviewComponent
@Composable
private fun HistoriaClinicaPrintablePreview() {
    val historiaClinica = previewHistoriaClinica(partial = false)
    HirshTheme {
        HistoriaClinicaPrintable(
            patient = previewPatient,
            hospitalizacion = previewHospitalizacion,
            historiaClinica = historiaClinica,
            onBack = {},
        )
    }
}
