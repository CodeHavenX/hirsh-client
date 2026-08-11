package com.cramsan.hirsh.ui.screens.evolucionview

import app.cash.turbine.test
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val samplePatient = Patient(
    id = "#00142",
    name = "Maria Gonzalez Huerta",
    dateOfBirth = "14/03/1989",
    phone = "987-654-321",
    assignedDoctor = "Dr. Patel",
    lastVisit = "12 Abr 2026",
    bloodType = "O+",
    allergies = "Penicilina",
    nationalId = "45678901",
    sex = Sex.FEMALE,
)

private fun sampleEvolucion(id: String) = Evolucion(
    id = id,
    fecha = "18 Jun 2026",
    hora = "22:30",
    medico = "Dr. Hirsh",
    vitals = Vitals(pa = "115/72", fc = "80", fr = "20", temp = "37.0", satO2 = "95", fio2 = "24"),
    diagnosticos = listOf(DiagnosticoCie10("B59", "Neumonia por Pneumocystis")),
    subjective = "Paciente refiere notable mejoria de disnea.",
    objective = "PA 115/72, FC 80, FR 20, SatO2 95% con FiO2 24%.",
    assessment = "Buena respuesta a tratamiento antimicrobiano.",
    plan = "Continuar TMP-SMX y corticoides en descenso.",
    rx = "TMP-SMX 15mg/kg/dia EV c/8h",
    pronostico = Pronostico.FAVORABLE,
    resultado = EvolucionResultado.FAVORABLE,
    examenes = emptyList(),
    examenesObs = "",
)

private fun sampleHospitalization(id: String, patientId: String, evoluciones: List<Evolucion> = emptyList()) = Hospitalizacion(
    id = id,
    patientId = patientId,
    servicio = "Medicina General",
    cama = "08",
    medicoResponsable = "Dr. Patel",
    fechaIngreso = "10 Abr 2026",
    horaIngreso = "09:00",
    fechaAlta = null,
    horaAlta = null,
    motivoIngreso = "Tos productiva con fiebre",
    estado = EstadoHospitalizacion.ACTIVA,
    historiaClinica = HistoriaClinica(),
    evoluciones = evoluciones,
)

private class FakePatientRepository(patients: List<Patient>) : PatientRepository {
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
    ): Patient = error("not used by this test")
}

private class FakeHospitalizationRepository(hospitalizations: List<Hospitalizacion> = emptyList()) :
    HospitalizationRepository {
    private val _hospitalizations = MutableStateFlow(hospitalizations)

    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        _hospitalizations.map { list -> list.filter { it.patientId == patientId } }

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        _hospitalizations.map { list -> list.find { it.patientId == patientId && it.id == hospId } }

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> =
        _hospitalizations.map { list -> list.find { it.id == hospId }?.evoluciones?.find { it.id == evoId } }

    override suspend fun addHospitalization(
        patientId: String,
        servicio: String,
        cama: String,
        medicoResponsable: String,
        motivoIngreso: String,
    ): Hospitalizacion = error("not used by this test")

    override suspend fun discharge(hospId: String) = Unit

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
}

@OptIn(ExperimentalCoroutinesApi::class)
class EvolucionViewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load populates patient, hospitalizacion, and evolucion`() = runTest(dispatcher) {
        val evolucion = sampleEvolucion("evo1")
        val hospitalization = sampleHospitalization("h1", samplePatient.id, listOf(evolucion))
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
        )

        viewModel.uiState.test {
            assertEquals(EvolucionViewUiState(), awaitItem())
            viewModel.load(samplePatient.id, hospitalization.id, evolucion.id)
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertEquals(hospitalization, loaded.hospitalizacion)
            assertEquals(evolucion, loaded.evolucion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when the hospId belongs to a different patient`() = runTest(dispatcher) {
        val evolucion = sampleEvolucion("evo1")
        val hospitalization = sampleHospitalization("h1", "#00999", listOf(evolucion))
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id, evolucion.id)
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertNull(loaded.hospitalizacion)
            assertNull(loaded.evolucion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when the patient does not exist`() = runTest(dispatcher) {
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(emptyList()),
            FakeHospitalizationRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load("#does-not-exist", "h1", "evo1")
            val loaded = awaitItem()
            assertNull(loaded.patient)
            assertNull(loaded.hospitalizacion)
            assertNull(loaded.evolucion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when evoId does not exist within the hospitalizacion`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id, listOf(sampleEvolucion("evo1")))
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id, "#does-not-exist")
            val loaded = awaitItem()
            assertEquals(hospitalization, loaded.hospitalizacion)
            assertNull(loaded.evolucion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectTab switches the selected tab`() = runTest(dispatcher) {
        val evolucion = sampleEvolucion("evo1")
        val hospitalization = sampleHospitalization("h1", samplePatient.id, listOf(evolucion))
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id, evolucion.id)
            assertEquals(EvolucionViewTab.EVOLUCION, awaitItem().selectedTab)

            viewModel.selectTab(EvolucionViewTab.EXAMENES)

            assertEquals(EvolucionViewTab.EXAMENES, awaitItem().selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openPrintPreview shows the summary, closePrintPreview hides it`() = runTest(dispatcher) {
        val evolucion = sampleEvolucion("evo1")
        val hospitalization = sampleHospitalization("h1", samplePatient.id, listOf(evolucion))
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id, evolucion.id)
            assertEquals(false, awaitItem().showPrintPreview)

            viewModel.openPrintPreview()
            assertEquals(true, awaitItem().showPrintPreview)

            viewModel.closePrintPreview()
            assertEquals(false, awaitItem().showPrintPreview)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
