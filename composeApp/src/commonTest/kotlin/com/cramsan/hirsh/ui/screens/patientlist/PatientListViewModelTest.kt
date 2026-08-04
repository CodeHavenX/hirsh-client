package com.cramsan.hirsh.ui.screens.patientlist

import app.cash.turbine.test
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private class FakePatientRepository(private val patients: List<Patient>) : PatientRepository {
    override suspend fun getPatients(): List<Patient> = patients
    override suspend fun getPatient(id: String): Patient? = patients.find { it.id == id }
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
}
