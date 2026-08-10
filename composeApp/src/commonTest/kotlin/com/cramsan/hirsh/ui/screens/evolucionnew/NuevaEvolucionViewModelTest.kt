package com.cramsan.hirsh.ui.screens.evolucionnew

import app.cash.turbine.test
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
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

private fun sampleHospitalization(id: String, patientId: String) = Hospitalizacion(
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
    evoluciones = emptyList(),
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
    var lastAddedEvolucion: Evolucion? = null
        private set
    var addEvolucionCallCount = 0
        private set

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

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion {
        addEvolucionCallCount++
        val created = evolucion.copy(id = "evo_new")
        lastAddedEvolucion = created
        _hospitalizations.update { list ->
            list.map { if (it.id == hospId) it.copy(evoluciones = listOf(created) + it.evoluciones) else it }
        }
        return created
    }
}

private class FakeSessionRepository(session: Session?) : SessionRepository {
    private val _session = MutableStateFlow(session)
    override val session: StateFlow<Session?> = _session.asStateFlow()
    override suspend fun login(username: String, password: String): Result<Session> = error("not used by this test")
    override fun logout() = Unit
}

private class FakeClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private val sampleSession = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)

@OptIn(ExperimentalCoroutinesApi::class)
class NuevaEvolucionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        patients: List<Patient> = listOf(samplePatient),
        hospitalizations: List<Hospitalizacion> = emptyList(),
        session: Session? = sampleSession,
    ) = NuevaEvolucionViewModel(
        FakePatientRepository(patients),
        FakeHospitalizationRepository(hospitalizations),
        FakeSessionRepository(session),
        FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
    )

    @Test
    fun `load populates patient and hospitalizacion`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val vm = viewModel(hospitalizations = listOf(hospitalization))

        vm.uiState.test {
            assertEquals(NuevaEvolucionUiState(), awaitItem())
            vm.load(samplePatient.id, hospitalization.id)
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertEquals(hospitalization, loaded.hospitalizacion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when the hospId belongs to a different patient`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", "#00999")
        val vm = viewModel(hospitalizations = listOf(hospitalization))

        vm.uiState.test {
            awaitItem()
            vm.load(samplePatient.id, hospitalization.id)
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertNull(loaded.hospitalizacion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when the patient does not exist`() = runTest(dispatcher) {
        val vm = viewModel(patients = emptyList())

        vm.uiState.test {
            awaitItem()
            vm.load("#does-not-exist", "h1")
            val loaded = awaitItem()
            assertNull(loaded.patient)
            assertNull(loaded.hospitalizacion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun filledState(vm: NuevaEvolucionViewModel) {
        vm.onSubjectiveChange("Refiere mejoria")
        vm.onObjectiveChange("PA 120/80")
        vm.onDxDescripcionChange(0, "Neumonia")
        vm.onPronosticoChange(Pronostico.FAVORABLE.toDisplayLabel())
        vm.onResultadoEvolucionChange(EvolucionResultado.FAVORABLE.toDisplayLabel())
    }

    @Test
    fun `save blocks when subjective is blank`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)
        vm.onSubjectiveChange("")

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.addEvolucionCallCount)
        assertEquals("Completa los campos requeridos", vm.uiState.value.error)
    }

    @Test
    fun `save blocks when objective is blank`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)
        vm.onObjectiveChange("")

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.addEvolucionCallCount)
        assertEquals("Completa los campos requeridos", vm.uiState.value.error)
    }

    @Test
    fun `save blocks when no diagnosis has a description`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)
        vm.onDxDescripcionChange(0, "")

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.addEvolucionCallCount)
        assertEquals("Completa los campos requeridos", vm.uiState.value.error)
    }

    @Test
    fun `save blocks when pronostico is blank`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)
        vm.onPronosticoChange("")

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.addEvolucionCallCount)
        assertEquals("Completa los campos requeridos", vm.uiState.value.error)
    }

    @Test
    fun `save blocks when resultado evolucion is blank`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)
        vm.onResultadoEvolucionChange("")

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.addEvolucionCallCount)
        assertEquals("Completa los campos requeridos", vm.uiState.value.error)
    }

    @Test
    fun `save succeeds, filters blank rows, defaults rx, and sets medico from the session display name`() =
        runTest(dispatcher) {
            val hospitalization = sampleHospitalization("h1", samplePatient.id)
            val repository = FakeHospitalizationRepository(listOf(hospitalization))
            val vm = NuevaEvolucionViewModel(
                FakePatientRepository(listOf(samplePatient)),
                repository,
                FakeSessionRepository(sampleSession),
                FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
            )
            vm.load(samplePatient.id, hospitalization.id)
            dispatcher.scheduler.advanceUntilIdle()
            filledState(vm)
            vm.addDxRow()
            vm.addExamRow()
            vm.onExamNombreChange(0, "Hemoglobina")

            vm.save()
            dispatcher.scheduler.advanceUntilIdle()

            val created = repository.lastAddedEvolucion
            requireNotNull(created)
            assertEquals(1, created.diagnosticos.size)
            assertEquals("Neumonia", created.diagnosticos.single().descripcion)
            assertEquals(1, created.examenes.size)
            assertEquals("Hemoglobina", created.examenes.single().nombre)
            assertEquals("—", created.rx)
            assertEquals("Dr. A. Patel", created.medico)
            assertEquals(Pronostico.FAVORABLE, created.pronostico)
            assertEquals(EvolucionResultado.FAVORABLE, created.resultado)
            assertEquals("evo_new", vm.uiState.value.createdEvolucionId)
            assertEquals(false, vm.uiState.value.isSaving)
        }

    @Test
    fun `save ignores a second call while already saving`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)

        vm.save()
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.addEvolucionCallCount)
    }

    @Test
    fun `removeDxRow never leaves the list empty`() {
        val vm = viewModel()
        vm.removeDxRow(0)

        assertEquals(listOf(DxRow()), vm.uiState.value.diagnosticos)
    }

    @Test
    fun `removeExamRow never leaves the list empty`() {
        val vm = viewModel()
        vm.removeExamRow(0)

        assertEquals(listOf(ExamRow()), vm.uiState.value.examenes)
    }

    @Test
    fun `addDxRow and addExamRow append a new blank row`() {
        val vm = viewModel()
        vm.addDxRow()
        vm.addExamRow()

        assertEquals(2, vm.uiState.value.diagnosticos.size)
        assertEquals(2, vm.uiState.value.examenes.size)
    }
}
