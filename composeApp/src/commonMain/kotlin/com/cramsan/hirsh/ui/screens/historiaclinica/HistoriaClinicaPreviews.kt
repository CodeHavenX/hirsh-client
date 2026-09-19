package com.cramsan.hirsh.ui.screens.historiaclinica

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.ExamenRegional
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.ui.preview.PreviewComponent
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme

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

private fun previewHistoriaClinica(partial: Boolean) = HistoriaClinica(
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

private fun previewHospitalizacion(historiaClinica: HistoriaClinica) = Hospitalizacion(
    id = "h_ricaldi_1",
    patientId = previewPatient.id,
    servicio = "Psiquiatria (UHSMA)",
    cama = "01",
    medicoResponsable = "Dr. Reyes",
    fechaIngreso = "20 May 2026",
    horaIngreso = "14:30",
    fechaAlta = null,
    horaAlta = null,
    motivoIngreso = "Heteroagresividad, psicosis, adicciones",
    estado = EstadoHospitalizacion.ACTIVA,
    historiaClinica = historiaClinica,
    evoluciones = emptyList(),
)

private fun previewUiState(
    hospitalizacion: Hospitalizacion,
    activeSection: HcSectionKey = HcSectionKey.FILIACION,
) = HistoriaClinicaUiState(
    isLoading = false,
    patient = previewPatient,
    hospitalizacion = hospitalizacion,
    activeSection = activeSection,
    fieldValues = fieldMapFor(activeSection, hospitalizacion.historiaClinica).orEmpty(),
    motivoIngresoDraft = hospitalizacion.historiaClinica.motivoIngreso.data ?: MotivoIngreso(),
)

@PreviewResponsive
@Composable
private fun HistoriaClinicaScreenFiliacionPreview() {
    val hospitalizacion = previewHospitalizacion(previewHistoriaClinica(partial = false))
    HirshTheme {
        HistoriaClinicaScreenContent(
            uiState = previewUiState(hospitalizacion),
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectSection = {},
            onMotivoOptionChange = { _, _, _ -> },
            onMotivoOtrosDetalleChange = { _, _ -> },
            onFieldChange = { _, _ -> },
            onSave = {},
        )
    }
}

/** Only Filiacion/Motivo/Enfermedad Actual complete -- exercises the rail's mixed done/pending markers. */
@PreviewResponsive
@Composable
private fun HistoriaClinicaScreenPartialPreview() {
    val hospitalizacion = previewHospitalizacion(previewHistoriaClinica(partial = true))
    HirshTheme {
        HistoriaClinicaScreenContent(
            uiState = previewUiState(hospitalizacion),
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectSection = {},
            onMotivoOptionChange = { _, _, _ -> },
            onMotivoOtrosDetalleChange = { _, _ -> },
            onFieldChange = { _, _ -> },
            onSave = {},
        )
    }
}

/** Motivo de Ingreso section active -- exercises the checkbox grid. */
@PreviewResponsive
@Composable
private fun HistoriaClinicaScreenMotivoIngresoPreview() {
    val hospitalizacion = previewHospitalizacion(previewHistoriaClinica(partial = false))
    HirshTheme {
        HistoriaClinicaScreenContent(
            uiState = previewUiState(hospitalizacion, activeSection = HcSectionKey.MOTIVO_INGRESO),
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectSection = {},
            onMotivoOptionChange = { _, _, _ -> },
            onMotivoOtrosDetalleChange = { _, _ -> },
            onFieldChange = { _, _ -> },
            onSave = {},
        )
    }
}

/** HISS-501's print-preview summary, standalone (not behind the Screen's Dialog) so Roborazzi can capture it. */
@PreviewComponent
@Composable
private fun HistoriaClinicaPrintablePreview() {
    val hospitalizacion = previewHospitalizacion(previewHistoriaClinica(partial = false))
    HirshTheme {
        HistoriaClinicaPrintable(
            patient = previewPatient,
            hospitalizacion = hospitalizacion,
            onBack = {},
        )
    }
}
