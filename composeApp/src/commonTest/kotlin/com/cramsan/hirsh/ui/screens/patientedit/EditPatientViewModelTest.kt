package com.cramsan.hirsh.ui.screens.patientedit

import app.cash.turbine.test
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
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
    id = "07c98942-3654-4034-b960-f3265814e214",
    medicalRecordNumber = "HC-00142",
    documentType = DocumentType.NID,
    documentNumber = "45678901",
    firstName = "Maria",
    lastName = "Gonzalez",
    secondLastName = "Huerta",
    fullName = "Maria Gonzalez Huerta",
    birthDate = "14/03/1989",
    phone = "987-654-321",
    bloodType = "O+",
    allergies = listOf(Allergy("a1", AllergyType.MEDICATION, "Penicilina", Severity.SEVERE)),
    sex = Sex.FEMALE,
)

private class FakePatientRepository(patients: List<Patient> = listOf(existingPatient)) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override suspend fun refresh() = Unit
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
        medicalRecordNumber: String,
        firstName: String,
        lastName: String,
        secondLastName: String,
        documentType: DocumentType,
        documentNumber: String,
        birthDate: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
    ): Patient = error("not used by EditPatientViewModel")

    /** When set, every allergy call throws this instead of succeeding. */
    var allergyFailure: ApiException? = null
    var allergyCalls = 0
        private set
    private var nextAllergyId = 1

    override suspend fun addAllergy(
        patientId: String,
        allergyType: AllergyType,
        description: String,
        severity: Severity?,
        observations: String,
    ): Allergy {
        allergyCalls++
        allergyFailure?.let { throw it }
        val allergy = Allergy("allergy-${nextAllergyId++}", allergyType, description, severity, observations)
        updateAllergies(patientId) { listOf(allergy) + it }
        return allergy
    }

    override suspend fun updateAllergy(patientId: String, allergyId: String, severity: Severity?, observations: String): Allergy {
        allergyCalls++
        allergyFailure?.let { throw it }
        val current = _patients.value.first { it.id == patientId }.allergies.first { it.id == allergyId }
        val updated = current.copy(severity = severity, observations = observations)
        updateAllergies(patientId) { list -> list.map { if (it.id == allergyId) updated else it } }
        return updated
    }

    override suspend fun deleteAllergy(patientId: String, allergyId: String) {
        allergyCalls++
        allergyFailure?.let { throw it }
        updateAllergies(patientId) { list -> list.filterNot { it.id == allergyId } }
    }

    private fun updateAllergies(patientId: String, transform: (List<Allergy>) -> List<Allergy>) {
        _patients.update { list -> list.map { if (it.id == patientId) it.copy(allergies = transform(it.allergies)) else it } }
    }
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
            assertEquals(existingPatient.firstName, state.firstName)
            assertEquals(existingPatient.lastName, state.lastName)
            assertEquals(existingPatient.secondLastName, state.secondLastName)
            assertEquals(existingPatient.documentNumber, state.documentNumber)
            assertEquals(existingPatient.birthDate, state.birthDate)
            assertEquals(existingPatient.phone, state.phone)
            assertEquals(existingPatient.sex, state.sex)
            assertEquals(existingPatient.bloodType, state.bloodType)
            assertEquals(existingPatient.allergies, state.allergies)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load for an unknown patient id leaves patient null and sets an error`() = runTest(dispatcher) {
        val viewModel = EditPatientViewModel(FakePatientRepository(emptyList()), FakeSessionRepository(), FakeClock())

        viewModel.uiState.test {
            awaitItem()
            viewModel.load("does-not-exist")
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
            viewModel.onFirstNameChange("")
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
        assertEquals("07c98942-3654-4034-b960-f3265814e214", update?.id)
        assertEquals("999-999-999", update?.newValues?.phone)
        assertEquals("apatel", update?.changedBy)
        assertEquals("15 Jan 2027", update?.fecha)
        assertEquals("10:30", update?.hora)
    }

    @Test
    fun `save preserves id and never carries allergy changes -- those are saved separately, immediately`() =
        runTest(dispatcher) {
            val repository = FakePatientRepository()
            val viewModel = EditPatientViewModel(repository, FakeSessionRepository(), FakeClock())

            viewModel.uiState.test {
                awaitItem()
                viewModel.load(existingPatient.id)
                awaitItem()
                viewModel.onPhoneChange("999-999-999")
                awaitItem()
                viewModel.save()
                awaitItem()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            val update = repository.lastUpdate
            assertEquals(existingPatient.id, update?.newValues?.id)
            assertEquals(existingPatient.allergies, update?.newValues?.allergies)
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

    // --- allergies (HISS-623) ---------------------------------------------------------------

    /** Loads [existingPatient] and settles, so each allergy test starts from a populated form. */
    private fun loadedViewModel(repository: FakePatientRepository = FakePatientRepository()): EditPatientViewModel {
        val viewModel = EditPatientViewModel(repository, FakeSessionRepository(), FakeClock())
        viewModel.load(existingPatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `adding an allergy saves it immediately and prepends it, without touching the demographic save`() =
        runTest(dispatcher) {
            val repository = FakePatientRepository()
            val viewModel = loadedViewModel(repository)

            viewModel.onStartAddAllergy()
            viewModel.onAllergyTypeChange(AllergyType.FOOD)
            viewModel.onAllergyDescriptionChange("Mariscos")
            viewModel.onAllergySeverityChange(Severity.MODERATE)
            viewModel.saveAllergyDraft()
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("Mariscos", "Penicilina"), state.allergies.map { it.description })
            assertEquals(Severity.MODERATE, state.allergies.first().severity)
            assertNull(state.allergyDraft, "the form closes once saved")
            assertEquals(false, state.isAllergyBusy)
            assertNull(repository.lastUpdate, "an allergy change must never go through updatePatient")
        }

    @Test
    fun `saving demographics after adding an allergy keeps the new allergy`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = loadedViewModel(repository)

        viewModel.onStartAddAllergy()
        viewModel.onAllergyTypeChange(AllergyType.FOOD)
        viewModel.onAllergyDescriptionChange("Mariscos")
        viewModel.saveAllergyDraft()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onPhoneChange("999-999-999")
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Mariscos", "Penicilina"), repository.lastUpdate?.newValues?.allergies?.map { it.description })
        assertEquals(
            listOf("Mariscos", "Penicilina"),
            repository.patients.value.single { it.id == existingPatient.id }.allergies.map { it.description },
            "a demographic save must not revert an allergy already saved on its own",
        )
    }

    @Test
    fun `adding an allergy without a type or description is blocked before any call`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = loadedViewModel(repository)

        viewModel.onStartAddAllergy()
        viewModel.onAllergyDescriptionChange("Mariscos")
        viewModel.saveAllergyDraft()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Indica el tipo y la descripcion de la alergia", viewModel.uiState.value.allergyError)
        assertEquals(0, repository.allergyCalls)

        viewModel.onAllergyTypeChange(AllergyType.FOOD)
        viewModel.onAllergyDescriptionChange("   ")
        viewModel.saveAllergyDraft()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.allergyCalls)
    }

    @Test
    fun `editing an allergy only changes severity and observations, never the agent`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        val penicilina = viewModel.uiState.value.allergies.single()

        viewModel.onStartEditAllergy(penicilina)
        viewModel.onAllergyTypeChange(AllergyType.FOOD)
        viewModel.onAllergyDescriptionChange("Otra cosa")
        viewModel.onAllergySeverityChange(Severity.MILD)
        viewModel.onAllergyObservationsChange("Revisado por alergologia")
        viewModel.saveAllergyDraft()
        dispatcher.scheduler.advanceUntilIdle()

        val updated = viewModel.uiState.value.allergies.single()
        assertEquals(AllergyType.MEDICATION, updated.allergyType)
        assertEquals("Penicilina", updated.description)
        assertEquals(Severity.MILD, updated.severity)
        assertEquals("Revisado por alergologia", updated.observations)
    }

    @Test
    fun `editing records the loaded severity so a graded allergy isn't offered a reset to ungraded`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.onStartEditAllergy(viewModel.uiState.value.allergies.single())

            assertEquals(Severity.SEVERE, viewModel.uiState.value.allergyDraft?.originalSeverity)
        }

    @Test
    fun `removing an allergy asks for confirmation first, then deletes it`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = loadedViewModel(repository)
        val penicilina = viewModel.uiState.value.allergies.single()

        viewModel.onRequestDeleteAllergy(penicilina)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(penicilina.id, viewModel.uiState.value.confirmingDeleteId)
        assertEquals(0, repository.allergyCalls, "requesting removal alone must not delete anything")

        viewModel.confirmDeleteAllergy()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList(), viewModel.uiState.value.allergies)
        assertNull(viewModel.uiState.value.confirmingDeleteId)
    }

    @Test
    fun `cancelling a removal keeps the allergy`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = loadedViewModel(repository)

        viewModel.onRequestDeleteAllergy(viewModel.uiState.value.allergies.single())
        viewModel.onCancelDeleteAllergy()
        viewModel.confirmDeleteAllergy()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.allergies.size)
        assertEquals(0, repository.allergyCalls)
    }

    @Test
    fun `a failed allergy save keeps the draft open and the list unchanged`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = loadedViewModel(repository)
        repository.allergyFailure = ApiException(ApiError.Unknown("boom"))

        viewModel.onStartAddAllergy()
        viewModel.onAllergyTypeChange(AllergyType.FOOD)
        viewModel.onAllergyDescriptionChange("Mariscos")
        viewModel.saveAllergyDraft()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("No se pudo guardar la alergia", state.allergyError)
        assertEquals("Mariscos", state.allergyDraft?.description, "nothing typed is lost on failure")
        assertEquals(listOf("Penicilina"), state.allergies.map { it.description })
        assertEquals(false, state.isAllergyBusy)
    }

    @Test
    fun `removing an allergy someone else already removed surfaces a distinct not-found message`() =
        runTest(dispatcher) {
            val repository = FakePatientRepository()
            val viewModel = loadedViewModel(repository)
            repository.allergyFailure = ApiException(ApiError.NotFound("a1"))

            viewModel.onRequestDeleteAllergy(viewModel.uiState.value.allergies.single())
            viewModel.confirmDeleteAllergy()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                "Esta alergia ya no existe. Recarga la pagina para ver la lista actual.",
                viewModel.uiState.value.allergyError,
            )
        }

    @Test
    fun `an allergy save is ignored while another allergy call is already in flight`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = loadedViewModel(repository)

        viewModel.onStartAddAllergy()
        viewModel.onAllergyTypeChange(AllergyType.FOOD)
        viewModel.onAllergyDescriptionChange("Mariscos")
        viewModel.saveAllergyDraft()
        viewModel.saveAllergyDraft()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.allergyCalls)
    }
}
