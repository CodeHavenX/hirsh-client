package com.cramsan.hirsh.ui.screens.patientedit

import app.cash.turbine.test
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.singleAllergyFromText
import com.cramsan.hirsh.model.summary
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
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
    medicalRecordNumber = "HC-00142",
    documentType = DocumentType.NID,
    documentNumber = "45678901",
    fullName = "Maria Gonzalez Huerta",
    birthDate = "14/03/1989",
    phone = "987-654-321",
    bloodType = "O+",
    allergies = singleAllergyFromText("Penicilina"),
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

    /** Simulates a concurrent edit landing between this test's `load()` and `save()`. */
    fun bumpJpaVersion(id: String) {
        _patients.update { list -> list.map { if (it.id == id) it.copy(jpaVersion = it.jpaVersion + 1) else it } }
    }

    override suspend fun updatePatient(
        id: String,
        newValues: Patient,
        changedBy: String,
        fecha: String,
        hora: String,
    ) {
        val current = _patients.value.find { it.id == id }
        if (current != null && current.jpaVersion != newValues.jpaVersion) {
            throw ApiException(ApiError.Conflict(id))
        }
        lastUpdate = Quad(id, newValues, changedBy, fecha, hora)
        _patients.update { list -> list.map { if (it.id == id) newValues else it } }
    }

    override suspend fun addPatient(
        name: String,
        documentType: DocumentType,
        documentNumber: String,
        birthDate: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
    ): Patient = error("not used by EditPatientViewModel")
}

private data class Quad(val id: String, val newValues: Patient, val changedBy: String, val fecha: String, val hora: String)

private class FakeSessionRepository(username: String? = "apatel") : SessionRepository {
    override val session: StateFlow<Session?> =
        MutableStateFlow(username?.let { Session(username = it, displayName = it, roles = listOf("PSYCHIATRIST")) })
    override val isRestoring: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override suspend fun login(username: String, password: String): Result<Session> = error("not used")
    override suspend fun restore() = Unit
    override suspend fun logout() = Unit
    override fun forceLogout() = Unit
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
            assertEquals(existingPatient.fullName, state.fullName)
            assertEquals(existingPatient.documentNumber, state.documentNumber)
            assertEquals(existingPatient.birthDate, state.birthDate)
            assertEquals(existingPatient.phone, state.phone)
            assertEquals(existingPatient.sex, state.sex)
            assertEquals(existingPatient.bloodType, state.bloodType)
            assertEquals(existingPatient.allergies.summary(), state.allergies)
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
    fun `save preserves id and converts the free-text allergies field back into a list`() = runTest(dispatcher) {
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
        assertEquals(emptyList(), update?.newValues?.allergies)
    }

    @Test
    fun `save against a stale jpaVersion surfaces a distinct conflict message, not the generic one`() =
        runTest(dispatcher) {
            val repository = FakePatientRepository(listOf(existingPatient))
            val viewModel = EditPatientViewModel(repository, FakeSessionRepository(), FakeClock())

            viewModel.uiState.test {
                awaitItem()
                viewModel.load(existingPatient.id)
                awaitItem()
                viewModel.onPhoneChange("999-999-999")
                awaitItem()

                // Someone else's edit lands between this load and this save, bumping the stored
                // version out from under the form's in-flight copy of it.
                repository.bumpJpaVersion(existingPatient.id)

                viewModel.save()
                awaitItem() // isSaving = true
                val done = awaitItem()
                assertEquals(false, done.saved)
                assertEquals(
                    "Otro usuario actualizo este paciente mientras editabas. Recarga la pagina para ver los " +
                        "cambios recientes.",
                    done.error,
                )
                cancelAndIgnoreRemainingEvents()
            }
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
