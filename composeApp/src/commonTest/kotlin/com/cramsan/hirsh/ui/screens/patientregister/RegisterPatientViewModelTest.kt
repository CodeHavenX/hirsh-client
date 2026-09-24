package com.cramsan.hirsh.ui.screens.patientregister

import app.cash.turbine.test
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.singleAllergyFromText
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.assembleFullName
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val existingPatient = Patient(
    id = "07c98942-3654-4034-b960-f3265814e214",
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
    override suspend fun refresh() = Unit
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }
    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> =
        MutableStateFlow(emptyList<PatientChangeLogEntry>())

    override suspend fun updatePatient(
        id: String,
        newValues: Patient,
        changedBy: String,
        fecha: String,
        hora: String,
    ) {
        _patients.update { list -> list.map { if (it.id == id) newValues else it } }
    }

    var addPatientCalls = 0
        private set

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
    ): Patient {
        addPatientCalls++
        val created = Patient(
            id = "new-patient-id",
            medicalRecordNumber = medicalRecordNumber,
            documentType = documentType,
            documentNumber = documentNumber,
            firstName = firstName,
            lastName = lastName,
            secondLastName = secondLastName,
            fullName = assembleFullName(firstName, lastName, secondLastName),
            birthDate = birthDate,
            phone = phone,
            bloodType = bloodType,
            allergies = singleAllergyFromText(allergies),
            sex = sex,
        )
        _patients.update { list -> list + created }
        return created
    }

    override suspend fun addAllergy(
        patientId: String,
        allergyType: AllergyType,
        description: String,
        severity: Severity?,
        observations: String,
    ): Allergy {
        if (description in failingDescriptions) error("simulated failure for $description")
        addAllergyCalls += AddAllergyCall(patientId, allergyType, description, severity, observations)
        return Allergy("server-${addAllergyCalls.size}", allergyType, description, severity, observations)
    }

    data class AddAllergyCall(
        val patientId: String,
        val allergyType: AllergyType,
        val description: String,
        val severity: Severity?,
        val observations: String,
    )

    /** Successful calls only, in the order they were made. */
    val addAllergyCalls = mutableListOf<AddAllergyCall>()

    /** addAllergy throws for any description in here -- clear it to let a retry through. */
    val failingDescriptions = mutableSetOf<String>()

    override suspend fun updateAllergy(patientId: String, allergyId: String, severity: Severity?, observations: String): Allergy =
        error("not used by this test")

    override suspend fun deleteAllergy(patientId: String, allergyId: String) = error("not used by this test")
}

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterPatientViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillRequiredFields(viewModel: RegisterPatientViewModel) {
        viewModel.onMedicalRecordNumberChange("HC-2027-000001")
        viewModel.onFirstNameChange("Nuevo")
        viewModel.onLastNameChange("Paciente")
        viewModel.onNationalIdChange("11223344")
        viewModel.onDateOfBirthChange("01/01/2000")
        viewModel.onPhoneChange("999-999-999")
        viewModel.onSexChange(Sex.MALE)
    }

    @Test
    fun `register blocks save when a required field is blank`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = RegisterPatientViewModel(repository)
        fillRequiredFields(viewModel)
        viewModel.onFirstNameChange("")

        viewModel.uiState.test {
            skipItems(1)
            viewModel.register()
            val state = awaitItem()
            assertEquals("Completa los campos requeridos", state.error)
            assertNull(state.registeredPatientId)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, repository.addPatientCalls)
    }

    @Test
    fun `register blocks save when sex is not selected`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = RegisterPatientViewModel(repository)
        viewModel.onMedicalRecordNumberChange("HC-2027-000001")
        viewModel.onFirstNameChange("Nuevo")
        viewModel.onLastNameChange("Paciente")
        viewModel.onNationalIdChange("11223344")
        viewModel.onDateOfBirthChange("01/01/2000")
        viewModel.onPhoneChange("999-999-999")

        viewModel.register()

        assertEquals(0, repository.addPatientCalls)
    }

    @Test
    fun `register succeeds with all required fields and blank optional fields`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = RegisterPatientViewModel(repository)
        fillRequiredFields(viewModel)

        viewModel.uiState.test {
            skipItems(1)
            viewModel.register()
            val saving = awaitItem()
            assertEquals(true, saving.isSaving)
            val done = awaitItem()
            assertEquals(false, done.isSaving)
            assertEquals("new-patient-id", done.registeredPatientId)
            assertNull(done.error)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.addPatientCalls)
    }

    @Test
    fun `register ignores a second call while a save is already in flight`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = RegisterPatientViewModel(repository)
        fillRequiredFields(viewModel)

        viewModel.register()
        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.addPatientCalls)
    }

    @Test
    fun `checkDuplicate flags a name substring match`() = runTest(dispatcher) {
        val viewModel = RegisterPatientViewModel(FakePatientRepository())
        viewModel.onFirstNameChange("gonzalez")

        viewModel.uiState.test {
            skipItems(1)
            viewModel.checkDuplicate()
            assertEquals(existingPatient, awaitItem().duplicateWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkDuplicate flags an exact national id match`() = runTest(dispatcher) {
        val viewModel = RegisterPatientViewModel(FakePatientRepository())
        viewModel.onNationalIdChange("45678901")

        viewModel.uiState.test {
            skipItems(1)
            viewModel.checkDuplicate()
            assertEquals(existingPatient, awaitItem().duplicateWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkDuplicate clears a previously-set warning when nothing matches anymore`() = runTest(dispatcher) {
        val viewModel = RegisterPatientViewModel(FakePatientRepository())
        viewModel.onFirstNameChange("gonzalez")
        viewModel.checkDuplicate()

        viewModel.uiState.test {
            assertEquals(existingPatient, awaitItem().duplicateWarning)
            viewModel.onFirstNameChange("Someone Else Entirely")
            awaitItem()
            viewModel.checkDuplicate()
            assertNull(awaitItem().duplicateWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun addLocalAllergy(
        viewModel: RegisterPatientViewModel,
        type: AllergyType,
        description: String,
        severity: Severity? = null,
        observations: String = "",
    ) {
        viewModel.onStartAddAllergy()
        viewModel.onAllergyTypeChange(type)
        viewModel.onAllergyDescriptionChange(description)
        viewModel.onAllergySeverityChange(severity)
        viewModel.onAllergyObservationsChange(observations)
        viewModel.saveAllergyDraft()
    }

    @Test
    fun `adding, editing and removing allergies only changes local state`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = RegisterPatientViewModel(repository)

        addLocalAllergy(viewModel, AllergyType.MEDICATION, "Penicilina", Severity.SEVERE)
        addLocalAllergy(viewModel, AllergyType.FOOD, "Mariscos")
        assertEquals(listOf("Penicilina", "Mariscos"), viewModel.uiState.value.allergies.map { it.description })
        assertNull(viewModel.uiState.value.allergyDraft)

        // Not saved yet, so even the type/agent can be corrected in place.
        val shellfish = viewModel.uiState.value.allergies[1]
        viewModel.onStartEditAllergy(shellfish)
        viewModel.onAllergyTypeChange(AllergyType.ENVIRONMENTAL)
        viewModel.onAllergyDescriptionChange("Polen")
        viewModel.saveAllergyDraft()
        val edited = viewModel.uiState.value.allergies[1]
        assertEquals(shellfish.id, edited.id)
        assertEquals(AllergyType.ENVIRONMENTAL, edited.allergyType)
        assertEquals("Polen", edited.description)

        viewModel.onRequestDeleteAllergy(viewModel.uiState.value.allergies[0])
        viewModel.confirmDeleteAllergy()
        assertEquals(listOf("Polen"), viewModel.uiState.value.allergies.map { it.description })

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, repository.addPatientCalls)
        assertTrue(repository.addAllergyCalls.isEmpty())
    }

    @Test
    fun `saveAllergyDraft requires a type and a description`() = runTest(dispatcher) {
        val viewModel = RegisterPatientViewModel(FakePatientRepository())
        viewModel.onStartAddAllergy()
        viewModel.onAllergyDescriptionChange("Penicilina")

        viewModel.saveAllergyDraft()

        assertEquals("Indica el tipo y la descripcion de la alergia", viewModel.uiState.value.allergyError)
        assertTrue(viewModel.uiState.value.allergies.isEmpty())
        assertEquals("Penicilina", viewModel.uiState.value.allergyDraft?.description)
    }

    @Test
    fun `register posts each allergy against the created patient, then navigates`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = RegisterPatientViewModel(repository)
        fillRequiredFields(viewModel)
        addLocalAllergy(viewModel, AllergyType.MEDICATION, "Penicilina", Severity.SEVERE, "Rash")
        addLocalAllergy(viewModel, AllergyType.FOOD, "Mariscos")

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.addPatientCalls)
        // Last entered goes first: the record lists newest first, so it shows them in entry order.
        assertEquals(
            listOf(
                FakePatientRepository.AddAllergyCall("new-patient-id", AllergyType.FOOD, "Mariscos", null, ""),
                FakePatientRepository.AddAllergyCall("new-patient-id", AllergyType.MEDICATION, "Penicilina", Severity.SEVERE, "Rash"),
            ),
            repository.addAllergyCalls,
        )
        assertEquals("new-patient-id", viewModel.uiState.value.registeredPatientId)
    }

    @Test
    fun `register makes no allergy calls when none were entered`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = RegisterPatientViewModel(repository)
        fillRequiredFields(viewModel)

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.addAllergyCalls.isEmpty())
        assertEquals("new-patient-id", viewModel.uiState.value.registeredPatientId)
    }

    @Test
    fun `a failed allergy keeps the user on the form with only the failed entries`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        repository.failingDescriptions += "Mariscos"
        val viewModel = RegisterPatientViewModel(repository)
        fillRequiredFields(viewModel)
        addLocalAllergy(viewModel, AllergyType.MEDICATION, "Penicilina")
        addLocalAllergy(viewModel, AllergyType.FOOD, "Mariscos")

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.registeredPatientId, "must not navigate away while an allergy is unsaved")
        assertEquals("new-patient-id", state.createdPatientId)
        assertTrue(state.isLocked)
        assertEquals(listOf("Mariscos"), state.allergies.map { it.description })
        assertEquals("Paciente registrado, pero no se pudieron guardar 1 alergia(s)", state.error)
        assertEquals(listOf("Penicilina"), repository.addAllergyCalls.map { it.description })

        // The patient already exists: registering again must not create a second one.
        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repository.addPatientCalls)
    }

    @Test
    fun `retryAllergies resends only the failed entries, then navigates`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        repository.failingDescriptions += "Mariscos"
        val viewModel = RegisterPatientViewModel(repository)
        fillRequiredFields(viewModel)
        addLocalAllergy(viewModel, AllergyType.MEDICATION, "Penicilina")
        addLocalAllergy(viewModel, AllergyType.FOOD, "Mariscos")
        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        repository.failingDescriptions.clear()
        viewModel.retryAllergies()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Penicilina", "Mariscos"), repository.addAllergyCalls.map { it.description })
        assertEquals("new-patient-id", viewModel.uiState.value.registeredPatientId)
        assertEquals(1, repository.addPatientCalls)
    }

    @Test
    fun `goToRecord leaves for the created patient without resending anything`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        repository.failingDescriptions += "Mariscos"
        val viewModel = RegisterPatientViewModel(repository)
        fillRequiredFields(viewModel)
        addLocalAllergy(viewModel, AllergyType.FOOD, "Mariscos")
        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.goToRecord()

        assertEquals("new-patient-id", viewModel.uiState.value.registeredPatientId)
        assertTrue(repository.addAllergyCalls.isEmpty())
    }
}
