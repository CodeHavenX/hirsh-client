package com.cramsan.hirsh.ui.screens.hospitalization

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

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

private class PreviewPatientRepository(patients: List<Patient>) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }
    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> = MutableStateFlow(emptyList())

    override suspend fun updatePatient(
        id: String,
        newValues: Patient,
        changedBy: String,
        fecha: String,
        hora: String,
    ) = Unit

    override suspend fun addPatient(
        name: String,
        nationalId: String,
        dateOfBirth: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
        assignedDoctor: String,
    ): Patient = error("not used by this preview")
}

private class PreviewHospitalizationRepository(hospitalizaciones: List<Hospitalizacion>) : HospitalizationRepository {
    private val _hospitalizaciones = MutableStateFlow(hospitalizaciones)

    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        _hospitalizaciones.map { list -> list.filter { it.patientId == patientId } }

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        _hospitalizaciones.map { list -> list.find { it.patientId == patientId && it.id == hospId } }

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> =
        _hospitalizaciones.map { list -> list.find { it.id == hospId }?.evoluciones?.find { it.id == evoId } }

    override suspend fun addHospitalization(
        patientId: String,
        servicio: String,
        cama: String,
        medicoResponsable: String,
        motivoIngreso: String,
    ): Hospitalizacion = error("not used by this preview")

    override suspend fun discharge(hospId: String) = Unit

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
}

@Preview
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
        HospitalizationScreen(
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onNewEvolucion = {},
            onOpenHistoriaClinica = {},
            onEvolucionSelected = {},
            viewModel = HospitalizationViewModel(
                PreviewPatientRepository(listOf(previewPatient)),
                PreviewHospitalizationRepository(listOf(hospitalizacion)),
            ),
        )
    }
}

/** estado = Alta -- exercises the "Dar de alta" button being hidden and the "Fecha alta" KV row. */
@Preview
@Composable
private fun HospitalizationScreenAltaPreview() {
    val hospitalizacion = previewHospitalizacion(
        id = "h_mendoza_2",
        estado = EstadoHospitalizacion.ALTA,
        evoluciones = listOf(previewEvolucion("v5c", EvolucionResultado.FAVORABLE)),
    )
    HirshTheme {
        HospitalizationScreen(
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onNewEvolucion = {},
            onOpenHistoriaClinica = {},
            onEvolucionSelected = {},
            viewModel = HospitalizationViewModel(
                PreviewPatientRepository(listOf(previewPatient)),
                PreviewHospitalizationRepository(listOf(hospitalizacion)),
            ),
        )
    }
}

/** No evoluciones yet -- exercises the empty-state copy. */
@Preview
@Composable
private fun HospitalizationScreenNoEvolucionesPreview() {
    val hospitalizacion = previewHospitalizacion(
        id = "h_mendoza_3",
        estado = EstadoHospitalizacion.ACTIVA,
        evoluciones = emptyList(),
    )
    HirshTheme {
        HospitalizationScreen(
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onNewEvolucion = {},
            onOpenHistoriaClinica = {},
            onEvolucionSelected = {},
            viewModel = HospitalizationViewModel(
                PreviewPatientRepository(listOf(previewPatient)),
                PreviewHospitalizationRepository(listOf(hospitalizacion)),
            ),
        )
    }
}
