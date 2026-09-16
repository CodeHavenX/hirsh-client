package com.cramsan.hirsh.ui.screens.evolucionview

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Examen
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.ui.preview.PreviewComponent
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewPatient = Patient(
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
)

private fun previewEvolucion(examenes: List<Examen>) = Evolucion(
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
    rx = "TMP-SMX 15mg/kg/dia EV c/8h\nPrednisona 40mg VO c/12h (en descenso)\nO2 por CBN 1L",
    pronostico = Pronostico.FAVORABLE,
    resultado = EvolucionResultado.FAVORABLE,
    examenes = examenes,
    examenesObs = if (examenes.isEmpty()) "" else "Mejoria gasometrica respecto al ingreso. BK de esputo aun pendiente.",
)

private fun previewHospitalizacion(evolucion: Evolucion) = Hospitalizacion(
    id = "h_mendoza_1",
    patientId = previewPatient.id,
    servicio = "Emergencia - Topico de Medicina",
    cama = "0",
    medicoResponsable = "Dr. Hirsh",
    fechaIngreso = "15 Jun 2026",
    horaIngreso = "12:50",
    fechaAlta = null,
    horaAlta = null,
    motivoIngreso = "Sintomas respiratorios",
    estado = EstadoHospitalizacion.ACTIVA,
    historiaClinica = HistoriaClinica(),
    evoluciones = listOf(evolucion),
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
) = EvolucionViewUiState(
    isLoading = false,
    patient = previewPatient,
    hospitalizacion = hospitalizacion,
    evolucion = evolucion,
    selectedTab = selectedTab,
)

@PreviewResponsive
@Composable
private fun EvolucionViewScreenPreview() {
    val evolucion = previewEvolucion(previewExamenes)
    val hospitalizacion = previewHospitalizacion(evolucion)
    HirshTheme {
        EvolucionViewScreenContent(
            uiState = previewUiState(hospitalizacion, evolucion),
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            evoId = evolucion.id,
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectTab = {},
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
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            evoId = evolucion.id,
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectTab = {},
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
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            evoId = evolucion.id,
            onClose = {},
            onOpenPrintPreview = {},
            onClosePrintPreview = {},
            onSelectTab = {},
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
