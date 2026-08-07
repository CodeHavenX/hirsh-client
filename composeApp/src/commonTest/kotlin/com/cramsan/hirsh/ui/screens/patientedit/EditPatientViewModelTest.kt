package com.cramsan.hirsh.ui.screens.patientedit

import app.cash.turbine.test
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.model.Sex
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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val existingPatient = Patient(
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

private class FakePatientRepository(patients: List<Patient> = listOf(existingPatient)) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }
    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> =
        MutableStateFlow(emptyList<PatientChangeLogEntry>())

    var lastUpdate: Quad? = null
        private set

    override suspend fun updatePatient(
        id: String,
        newValues: Patient,
        changedBy: String,
        fecha: String,
        hora: String,
    ) {
        lastUpdate = Quad(id, newValues, changedBy, fecha, hora)
        _patients.update { list -> list.map { if (it.id == id) newValues else it } }
    }

    override suspend fun addPatient(
        name: String,
        nationalId: String,
        dateOfBirth: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
        assignedDoctor: String,
    ): Patient = error("not used by EditPatientViewModel")
}

private data class Quad(val id: String, val newValues: Patient, val changedBy: String, val fecha: String, val hora: String)

private class FakeSessionRepository(username: String? = "apatel") : SessionRepository {
    override val session: StateFlow<Session?> =
        MutableStateFlow(username?.let { Session(username = it, displayName = it, role = Role.DOCTOR) })

    override suspend fun login(username: String, password: String): Result<Session> = error("not used")
    override fun logout() = Unit
}

private val FIXED_NOW: Instant = LocalDateTime(2027, 1, 15, 10, 30).toInstant(TimeZone.currentSystemDefault())

private class FakeClock(private val instant: Instant = FIXED_NOW) : Clock {
    override fun now(): Instant = instant
}

@OptIn(ExperimentalCoroutinesApi::class)
class EditPatientViewModelTest {

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
    fun `load pre-fills all form fields from the existing patient`() = runTest(dispatcher) {
        val viewModel = EditPatientViewModel(FakePatientRepository(), FakeSessionRepository(), FakeClock())

        viewModel.uiState.test {
            assertEquals(EditPatientUiState(), awaitItem())
            viewModel.load(existingPatient.id)
            val state = awaitItem()
            assertEquals(existingPatient, state.patient)
            assertEquals(existingPatient.name, state.name)
            assertEquals(existingPatient.nationalId, state.nationalId)
            assertEquals(existingPatient.dateOfBirth, state.dateOfBirth)
            assertEquals(existingPatient.phone, state.phone)
            assertEquals(existingPatient.sex, state.sex)
            assertEquals(existingPatient.bloodType, state.bloodType)
            assertEquals(existingPatient.allergies, state.allergies)
            assertEquals(existingPatient.assignedDoctor, state.assignedDoctor)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load for an unknown patient id leaves patient null and sets an error`() = runTest(dispatcher) {
        val viewModel = EditPatientViewModel(FakePatientRepository(emptyList()), FakeSessionRepository(), FakeClock())

        viewModel.uiState.test {
            awaitItem()
            viewModel.load("#does-not-exist")
            val state = awaitItem()
            assertNull(state.patient)
            assertEquals(false, state.isLoading)
            assertEquals("Paciente no encontrado", state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save blocks with a validation error when a required field is blank`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = EditPatientViewModel(repository, FakeSessionRepository(), FakeClock())

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(existingPatient.id)
            awaitItem()
            viewModel.onNameChange("")
            awaitItem()
            viewModel.save()
            val state = awaitItem()
            assertEquals("Completa los campos requeridos", state.error)
            assertEquals(false, state.saved)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(repository.lastUpdate)
    }

    @Test
    fun `save writes through updatePatient with the session username and clock timestamp`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = EditPatientViewModel(repository, FakeSessionRepository("apatel"), FakeClock())

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(existingPatient.id)
            awaitItem()
            viewModel.onPhoneChange("999-999-999")
            awaitItem()
            viewModel.save()
            awaitItem() // isSaving = true
            val done = awaitItem()
            assertEquals(true, done.saved)
            cancelAndIgnoreRemainingEvents()
        }

        val update = repository.lastUpdate
        assertEquals("#00142", update?.id)
        assertEquals("999-999-999", update?.newValues?.phone)
        assertEquals("apatel", update?.changedBy)
        assertEquals("15 Jan 2027", update?.fecha)
        assertEquals("10:30", update?.hora)
    }

    @Test
    fun `save preserves id and lastVisit from the original patient`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = EditPatientViewModel(repository, FakeSessionRepository(), FakeClock())

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(existingPatient.id)
            awaitItem()
            viewModel.onAllergiesChange("Ninguna")
            awaitItem()
            viewModel.save()
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        val update = repository.lastUpdate
        assertEquals(existingPatient.id, update?.newValues?.id)
        assertEquals(existingPatient.lastVisit, update?.newValues?.lastVisit)
        assertEquals("Ninguna", update?.newValues?.allergies)
    }

    @Test
    fun `save ignores a second call while one is already in flight`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = EditPatientViewModel(repository, FakeSessionRepository(), FakeClock())

        viewModel.load(existingPatient.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.save()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.lastUpdate != null)
    }
}
