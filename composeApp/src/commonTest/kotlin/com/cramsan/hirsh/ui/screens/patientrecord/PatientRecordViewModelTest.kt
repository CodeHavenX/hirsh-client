package com.cramsan.hirsh.ui.screens.patientrecord

import app.cash.turbine.test
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PatientRepository
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

private class FakePatientRepository(patients: List<Patient>) : PatientRepository {
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
    ) {
        _patients.update { list -> list.map { if (it.id == id) newValues else it } }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PatientRecordViewModelTest {

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
    fun `load populates the patient when it exists`() = runTest(dispatcher) {
        val viewModel = PatientRecordViewModel(FakePatientRepository(listOf(samplePatient)))

        viewModel.uiState.test {
            assertEquals(PatientRecordUiState(), awaitItem())
            viewModel.load(samplePatient.id)
            assertEquals(PatientRecordUiState(isLoading = false, patient = samplePatient), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load leaves patient null when no match is found`() = runTest(dispatcher) {
        val viewModel = PatientRecordViewModel(FakePatientRepository(emptyList()))

        viewModel.uiState.test {
            assertEquals(PatientRecordUiState(), awaitItem())
            viewModel.load("#does-not-exist")
            assertEquals(PatientRecordUiState(isLoading = false, patient = null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
