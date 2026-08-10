package com.cramsan.hirsh.ui.screens.evolucionview

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Examen
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
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

private class PreviewPatientRepository : PatientRepository {
    private val _patients = MutableStateFlow(listOf(previewPatient))
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

private class PreviewHospitalizationRepository(hospitalizacion: Hospitalizacion) : HospitalizationRepository {
    private val _hospitalizaciones = MutableStateFlow(listOf(hospitalizacion))

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

@Preview
@Composable
private fun EvolucionViewScreenPreview() {
    val evolucion = previewEvolucion(previewExamenes)
    val hospitalizacion = previewHospitalizacion(evolucion)
    HirshTheme {
        EvolucionViewScreen(
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            evoId = evolucion.id,
            onClose = {},
            viewModel = EvolucionViewViewModel(PreviewPatientRepository(), PreviewHospitalizationRepository(hospitalizacion)),
        )
    }
}

/** No examenes recorded -- exercises the Examenes tab's empty-state box. */
@Preview
@Composable
private fun EvolucionViewScreenNoExamenesPreview() {
    val evolucion = previewEvolucion(emptyList())
    val hospitalizacion = previewHospitalizacion(evolucion)
    HirshTheme {
        EvolucionViewScreen(
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            evoId = evolucion.id,
            onClose = {},
            viewModel = EvolucionViewViewModel(PreviewPatientRepository(), PreviewHospitalizationRepository(hospitalizacion)),
        )
    }
}

/** Examenes tab selected. */
@Preview
@Composable
private fun EvolucionViewScreenExamenesTabPreview() {
    val evolucion = previewEvolucion(previewExamenes)
    val hospitalizacion = previewHospitalizacion(evolucion)
    val viewModel = EvolucionViewViewModel(PreviewPatientRepository(), PreviewHospitalizationRepository(hospitalizacion))
    viewModel.load(previewPatient.id, hospitalizacion.id, evolucion.id)
    viewModel.selectTab(EvolucionViewTab.EXAMENES)
    HirshTheme {
        EvolucionViewScreen(
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            evoId = evolucion.id,
            onClose = {},
            viewModel = viewModel,
        )
    }
}
