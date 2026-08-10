package com.cramsan.hirsh.ui.screens.admision

import app.cash.turbine.test
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Sex
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

private class FakePatientRepository(patients: List<Patient> = listOf(samplePatient)) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }
    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> =
        MutableStateFlow(emptyList<PatientChangeLogEntry>())

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
    ): Patient = error("not used in this test")
}

private class FakeHospitalizationRepository : HospitalizationRepository {
    var addHospitalizationCalls = 0
        private set
    var lastMotivoIngreso: String? = null
        private set

    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        MutableStateFlow(emptyList<Hospitalizacion>())

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        MutableStateFlow(null)

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> = MutableStateFlow(null)

    override suspend fun addHospitalization(
        patientId: String,
        servicio: String,
        cama: String,
        medicoResponsable: String,
        motivoIngreso: String,
    ): Hospitalizacion {
        addHospitalizationCalls++
        lastMotivoIngreso = motivoIngreso
        return Hospitalizacion(
            id = "h_new",
            patientId = patientId,
            servicio = servicio,
            cama = cama,
            medicoResponsable = medicoResponsable,
            fechaIngreso = "10 Abr 2026",
            horaIngreso = "09:00",
            fechaAlta = null,
            horaAlta = null,
            motivoIngreso = motivoIngreso,
            estado = EstadoHospitalizacion.ACTIVA,
            historiaClinica = HistoriaClinica(),
            evoluciones = emptyList(),
        )
    }

    override suspend fun discharge(hospId: String) = Unit

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdmisionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillRequiredFields(viewModel: AdmisionViewModel) {
        viewModel.onServicioChange("Medicina General")
        viewModel.onCamaChange("12")
        viewModel.onMedicoResponsableChange("Dr. Patel")
    }

    @Test
    fun `load populates the patient`() = runTest(dispatcher) {
        val viewModel = AdmisionViewModel(FakePatientRepository(), FakeHospitalizationRepository())

        viewModel.uiState.test {
            skipItems(1)
            viewModel.load(samplePatient.id)
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertEquals(false, loaded.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load leaves patient null when the patient does not exist`() = runTest(dispatcher) {
        val viewModel = AdmisionViewModel(FakePatientRepository(emptyList()), FakeHospitalizationRepository())

        viewModel.uiState.test {
            skipItems(1)
            viewModel.load("#unknown")
            val loaded = awaitItem()
            assertEquals(null, loaded.patient)
            assertEquals(false, loaded.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register does nothing when the patient was not found`() = runTest(dispatcher) {
        val repository = FakeHospitalizationRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(emptyList()), repository)
        viewModel.load("#unknown")
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register blocks save when servicio is blank`() = runTest(dispatcher) {
        val repository = FakeHospitalizationRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)
        viewModel.onServicioChange("")

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register blocks save when cama is blank`() = runTest(dispatcher) {
        val repository = FakeHospitalizationRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)
        viewModel.onCamaChange("")

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register blocks save when medico responsable is blank`() = runTest(dispatcher) {
        val repository = FakeHospitalizationRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)
        viewModel.onMedicoResponsableChange("")

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register defaults a blank motivo to an em dash`() = runTest(dispatcher) {
        val repository = FakeHospitalizationRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.uiState.test {
            skipItems(1)
            viewModel.register()
            val saving = awaitItem()
            assertEquals(true, saving.isSaving)
            val done = awaitItem()
            assertEquals(false, done.isSaving)
            assertEquals("h_new", done.createdHospitalizationId)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.addHospitalizationCalls)
        assertEquals("—", repository.lastMotivoIngreso)
    }

    @Test
    fun `register succeeds with all fields filled`() = runTest(dispatcher) {
        val repository = FakeHospitalizationRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)
        viewModel.onMotivoChange("Sintomas respiratorios")

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.addHospitalizationCalls)
        assertEquals("Sintomas respiratorios", repository.lastMotivoIngreso)
    }

    @Test
    fun `register ignores a second call while a save is already in flight`() = runTest(dispatcher) {
        val repository = FakeHospitalizationRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.register()
        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.addHospitalizationCalls)
    }
}
