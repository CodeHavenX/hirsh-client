package com.cramsan.hirsh.ui.screens.historiaclinica

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.ExamenRegional
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Sex
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
    id = "#00129",
    name = "Karla Sofia Ricaldi Sedano",
    dateOfBirth = "15/10/2012",
    phone = "998-984-134",
    assignedDoctor = "Dr. Reyes",
    lastVisit = "20 May 2026",
    bloodType = "—",
    allergies = "Ninguna",
    nationalId = "70083906",
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

@Preview
@Composable
private fun HistoriaClinicaScreenFiliacionPreview() {
    val hospitalizacion = previewHospitalizacion(previewHistoriaClinica(partial = false))
    HirshTheme {
        HistoriaClinicaScreen(
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onClose = {},
            viewModel = HistoriaClinicaViewModel(PreviewPatientRepository(), PreviewHospitalizationRepository(hospitalizacion)),
        )
    }
}

/** Only Filiacion/Motivo/Enfermedad Actual complete -- exercises the rail's mixed done/pending markers. */
@Preview
@Composable
private fun HistoriaClinicaScreenPartialPreview() {
    val hospitalizacion = previewHospitalizacion(previewHistoriaClinica(partial = true))
    HirshTheme {
        HistoriaClinicaScreen(
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onClose = {},
            viewModel = HistoriaClinicaViewModel(PreviewPatientRepository(), PreviewHospitalizationRepository(hospitalizacion)),
        )
    }
}

/** Motivo de Ingreso section active -- exercises the checkbox grid. */
@Preview
@Composable
private fun HistoriaClinicaScreenMotivoIngresoPreview() {
    val hospitalizacion = previewHospitalizacion(previewHistoriaClinica(partial = false))
    val viewModel = HistoriaClinicaViewModel(PreviewPatientRepository(), PreviewHospitalizationRepository(hospitalizacion))
    viewModel.load(previewPatient.id, hospitalizacion.id)
    viewModel.selectSection(HcSectionKey.MOTIVO_INGRESO)
    HirshTheme {
        HistoriaClinicaScreen(
            patientId = previewPatient.id,
            hospId = hospitalizacion.id,
            onClose = {},
            viewModel = viewModel,
        )
    }
}

/** HISS-501's print-preview summary, standalone (not behind the Screen's Dialog) so Roborazzi can capture it. */
@Preview
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
