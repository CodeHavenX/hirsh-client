package com.cramsan.hirsh.ui.screens.hospitalization

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewPatient = Patient(
    id = "#00135",
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

private fun previewEvolucion(id: String, resultado: EvolucionResultado) = Evolucion(
    id = id,
    fecha = "18 Jun 2026",
    hora = "22:30",
    medico = "Dr. Hirsh",
    vitals = Vitals(pa = "115/72", fc = "80", fr = "20", temp = "37.0", satO2 = "95", fio2 = "24"),
    diagnosticos = listOf(DiagnosticoCie10("B59", "Neumonia por Pneumocystis")),
    subjective = "Paciente refiere notable mejoria de disnea. Tolera destete progresivo de oxigeno.",
    objective = "PA 115/72, FC 80, FR 20, SatO2 95% con FiO2 24%.",
    assessment = "Buena respuesta a tratamiento antimicrobiano.",
    plan = "Continuar TMP-SMX y corticoides en descenso.",
    rx = "TMP-SMX 15mg/kg/dia EV c/8h",
    pronostico = Pronostico.FAVORABLE,
    resultado = resultado,
    examenes = emptyList(),
    examenesObs = "",
)

private fun previewHospitalizacion(
    id: String,
    estado: EstadoHospitalizacion,
    evoluciones: List<Evolucion>,
) = Hospitalizacion(
    id = id,
    patientId = previewPatient.id,
    servicio = "Emergencia - Topico de Medicina",
    cama = "0",
    medicoResponsable = "Dr. Hirsh",
    fechaIngreso = "15 Jun 2026",
    horaIngreso = "12:50",
    fechaAlta = if (estado == EstadoHospitalizacion.ALTA) "20 Jun 2026" else null,
    horaAlta = if (estado == EstadoHospitalizacion.ALTA) "10:00" else null,
    motivoIngreso = "Sintomas respiratorios",
    estado = estado,
    historiaClinica = HistoriaClinica(
        diagnostico = HcSection(
            complete = true,
            data = Diagnostico(ejeI = "—", ejeII = "—", ejeIII = "Neumonia (B59)", ejeIV = "—", ejeV = "—"),
        ),
        plan = HcSection(
            complete = true,
            data = Plan(
                lugarHospitalizacion = "Emergencia",
                examenesSolicitados = "—",
                psicofarmacos = "—",
                evaluacionesSolicitadas = "—",
            ),
        ),
    ),
    evoluciones = evoluciones,
)

private fun previewUiState(hospitalizacion: Hospitalizacion) = HospitalizationUiState(
    isLoading = false,
    patient = previewPatient,
    hospitalizacion = hospitalizacion,
)

@PreviewResponsive
@Composable
private fun HospitalizationScreenActivaPreview() {
    val hospitalizacion = previewHospitalizacion(
        id = "h_mendoza_1",
        estado = EstadoHospitalizacion.ACTIVA,
        evoluciones = listOf(
            previewEvolucion("v5c", EvolucionResultado.FAVORABLE),
            previewEvolucion("v5", EvolucionResultado.ESTACIONARIA),
        ),
    )
    HirshTheme {
        HospitalizationScreenContent(
            uiState = previewUiState(hospitalizacion),
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onNewEvolucion = {},
            onOpenHistoriaClinica = {},
            onEvolucionSelected = {},
            onDischarge = {},
        )
    }
}

/** estado = Alta -- exercises the "Dar de alta" button being hidden and the "Fecha alta" KV row. */
@PreviewResponsive
@Composable
private fun HospitalizationScreenAltaPreview() {
    val hospitalizacion = previewHospitalizacion(
        id = "h_mendoza_2",
        estado = EstadoHospitalizacion.ALTA,
        evoluciones = listOf(previewEvolucion("v5c", EvolucionResultado.FAVORABLE)),
    )
    HirshTheme {
        HospitalizationScreenContent(
            uiState = previewUiState(hospitalizacion),
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onNewEvolucion = {},
            onOpenHistoriaClinica = {},
            onEvolucionSelected = {},
            onDischarge = {},
        )
    }
}

/** No evoluciones yet -- exercises the empty-state copy. */
@PreviewResponsive
@Composable
private fun HospitalizationScreenNoEvolucionesPreview() {
    val hospitalizacion = previewHospitalizacion(
        id = "h_mendoza_3",
        estado = EstadoHospitalizacion.ACTIVA,
        evoluciones = emptyList(),
    )
    HirshTheme {
        HospitalizationScreenContent(
            uiState = previewUiState(hospitalizacion),
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onNewEvolucion = {},
            onOpenHistoriaClinica = {},
            onEvolucionSelected = {},
            onDischarge = {},
        )
    }
}
