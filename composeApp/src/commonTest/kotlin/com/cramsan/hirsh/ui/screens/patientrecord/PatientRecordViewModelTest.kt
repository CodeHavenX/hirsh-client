package com.cramsan.hirsh.ui.screens.patientrecord

import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

private class FakePatientRepository(private val patients: List<Patient>) : PatientRepository {
    override suspend fun getPatients(): List<Patient> = patients
    override suspend fun getPatient(id: String): Patient? = patients.find { it.id == id }
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

        viewModel.load(samplePatient.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(samplePatient, state.patient)
    }

    @Test
    fun `load leaves patient null when no match is found`() = runTest(dispatcher) {
        val viewModel = PatientRecordViewModel(FakePatientRepository(emptyList()))

        viewModel.load("#does-not-exist")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.patient)
    }
}
