package com.cramsan.hirsh.ui.screens.patienthistory

import app.cash.turbine.test
import com.cramsan.hirsh.model.AccessAction
import com.cramsan.hirsh.model.AccessLogEntry
import com.cramsan.hirsh.model.AccessLogPage
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.AccessLogRepository
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

private val readEntry = AccessLogEntry("e1", "3f2504e0-4f89-41d3-9a0c-0305e82c3301", AccessAction.RECORD_READ, "24 Sep 2026 · 11:05", "10.20.30.41")
private val modifiedEntry = readEntry.copy(id = "e2", action = AccessAction.PATIENT_MODIFIED, occurredAt = "24 Sep 2026 · 11:02")

private class FakePatientRepository(patients: List<Patient> = listOf(samplePatient)) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override suspend fun refresh() = Unit
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }

    override suspend fun updatePatient(id: String, newValues: Patient) = Unit

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
    ): Patient = error("not used by PatientHistoryViewModel")

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

/** Emits [page], or throws [failure] instead when it's set. */
private class FakeAccessLogRepository(
    private val page: AccessLogPage = AccessLogPage(emptyList(), 0),
    private val failure: Exception? = null,
) : AccessLogRepository {
    val requestedPatientIds = mutableListOf<String>()

    override fun getPatientAccessLog(patientId: String): Flow<AccessLogPage> = flow {
        requestedPatientIds += patientId
        failure?.let { throw it }
        emit(page)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PatientHistoryViewModelTest {

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
    fun `load shows the patient's access entries in the order the server returned them`() = runTest(dispatcher) {
        val accessLog = FakeAccessLogRepository(AccessLogPage(listOf(readEntry, modifiedEntry), totalEntries = 2))
        val viewModel = PatientHistoryViewModel(FakePatientRepository(), accessLog)

        viewModel.uiState.test {
            assertEquals(PatientHistoryUiState(), awaitItem())
            viewModel.load(samplePatient.id)
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(samplePatient, state.patient)
            assertEquals(listOf(readEntry, modifiedEntry), state.entries)
            assertNull(state.error)
            assertFalse(state.isTruncated)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(samplePatient.id), accessLog.requestedPatientIds)
    }

    @Test
    fun `a trail longer than the fetched page is flagged as truncated`() = runTest(dispatcher) {
        val accessLog = FakeAccessLogRepository(AccessLogPage(listOf(readEntry, modifiedEntry), totalEntries = 132))
        val viewModel = PatientHistoryViewModel(FakePatientRepository(), accessLog)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            val state = awaitItem()
            assertTrue(state.isTruncated)
            assertEquals(132, state.totalEntries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a patient nobody has accessed yields an empty list, not an error`() = runTest(dispatcher) {
        val viewModel = PatientHistoryViewModel(FakePatientRepository(), FakeAccessLogRepository())

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            val state = awaitItem()
            assertEquals(emptyList(), state.entries)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a 403 shows the no-permission state and still shows the patient`() = runTest(dispatcher) {
        val accessLog = FakeAccessLogRepository(failure = ApiException(ApiError.Forbidden))
        val viewModel = PatientHistoryViewModel(FakePatientRepository(), accessLog)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            val state = awaitItem()
            assertEquals(AccessLogError.FORBIDDEN, state.error)
            assertEquals(samplePatient, state.patient)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `any other failure shows the generic error state`() = runTest(dispatcher) {
        val accessLog = FakeAccessLogRepository(failure = ApiException(ApiError.Unknown("boom")))
        val viewModel = PatientHistoryViewModel(FakePatientRepository(), accessLog)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(AccessLogError.FAILED, awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
