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
    ): Allergy = error("not used by this test")

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
}
