package com.cramsan.hirsh.ui.screens.patientlist

import app.cash.turbine.test
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.assembleFullName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

private val samplePatient = Patient(
    id = "07c98942-3654-4034-b960-f3265814e214",
    medicalRecordNumber = "HC-00142",
    documentType = DocumentType.NID,
    documentNumber = "45678901",
    fullName = "Maria Gonzalez Huerta",
    birthDate = "14/03/1989",
    phone = "987-654-321",
    bloodType = "O+",
    allergies = listOf(Allergy("allergy_Penicilina", AllergyType.OTHER, "Penicilina", null)),
    sex = Sex.FEMALE,
)

private val otherPatient = Patient(
    id = "a21f8bfa-299c-42db-9681-c84f87a90ce4",
    medicalRecordNumber = "HC-00138",
    documentType = DocumentType.NID,
    documentNumber = "09147875",
    fullName = "Eduardo Remon Huertas",
    birthDate = "17/07/1962",
    phone = "912-345-678",
    bloodType = "A+",
    allergies = emptyList(),
    sex = Sex.MALE,
)

private class FakePatientRepository(patients: List<Patient>) : PatientRepository {
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
    ): Patient {
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
            allergies = emptyList(),
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
class PatientListViewModelTest {

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
    fun `loads patients from the repository on init`() = runTest(dispatcher) {
        val viewModel = PatientListViewModel(FakePatientRepository(listOf(samplePatient)))

        viewModel.uiState.test {
            assertEquals(PatientListUiState(isLoading = true), awaitItem())
            assertEquals(PatientListUiState(isLoading = false, patients = listOf(samplePatient)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces an empty list when the repository has no patients`() = runTest(dispatcher) {
        val viewModel = PatientListViewModel(FakePatientRepository(emptyList()))

        viewModel.uiState.test {
            skipItems(1)
            assertEquals(emptyList(), awaitItem().patients)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters by a name substring case-insensitively`() = runTest(dispatcher) {
        val viewModel = PatientListViewModel(FakePatientRepository(listOf(samplePatient, otherPatient)))

        viewModel.uiState.test {
            skipItems(1)
            awaitItem()
            viewModel.onQueryChange("gonzalez")
            assertEquals(listOf(samplePatient), awaitItem().patients)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters by a national id substring`() = runTest(dispatcher) {
        val viewModel = PatientListViewModel(FakePatientRepository(listOf(samplePatient, otherPatient)))

        viewModel.uiState.test {
            skipItems(1)
            awaitItem()
            viewModel.onQueryChange("09147875")
            assertEquals(listOf(otherPatient), awaitItem().patients)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters by an HCL id substring`() = runTest(dispatcher) {
        val viewModel = PatientListViewModel(FakePatientRepository(listOf(samplePatient, otherPatient)))

        viewModel.uiState.test {
            skipItems(1)
            awaitItem()
            viewModel.onQueryChange("00142")
            assertEquals(listOf(samplePatient), awaitItem().patients)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blank query surfaces every patient`() = runTest(dispatcher) {
        val viewModel = PatientListViewModel(FakePatientRepository(listOf(samplePatient, otherPatient)))

        viewModel.uiState.test {
            skipItems(1)
            awaitItem()
            viewModel.onQueryChange("maria")
            awaitItem()
            viewModel.onQueryChange("")
            assertEquals(listOf(samplePatient, otherPatient), awaitItem().patients)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a query matching nothing surfaces an empty list`() = runTest(dispatcher) {
        val viewModel = PatientListViewModel(FakePatientRepository(listOf(samplePatient, otherPatient)))

        viewModel.uiState.test {
            skipItems(1)
            awaitItem()
            viewModel.onQueryChange("does-not-match-anyone")
            assertEquals(emptyList(), awaitItem().patients)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState reflects the current query`() = runTest(dispatcher) {
        val viewModel = PatientListViewModel(FakePatientRepository(listOf(samplePatient)))

        viewModel.uiState.test {
            skipItems(1)
            awaitItem()
            viewModel.onQueryChange("maria")
            assertEquals("maria", awaitItem().query)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
