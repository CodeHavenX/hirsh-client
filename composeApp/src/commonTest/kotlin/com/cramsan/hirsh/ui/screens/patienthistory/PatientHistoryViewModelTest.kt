package com.cramsan.hirsh.ui.screens.patienthistory

import app.cash.turbine.test
import com.cramsan.hirsh.model.FieldChange
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

private class FakePatientRepository(
    patients: List<Patient> = listOf(samplePatient),
    changeLog: List<PatientChangeLogEntry> = emptyList(),
) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    private val _changeLog = MutableStateFlow(changeLog)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }
    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> = _changeLog.asStateFlow()

    fun pushChangeLog(entries: List<PatientChangeLogEntry>) {
        _changeLog.update { entries }
    }

    override suspend fun updatePatient(
        id: String,
        newValues: Patient,
        changedBy: String,
        fecha: String,
        hora: String,
    ) = Unit

    override suspend fun addPatient(
        name: String,
        nationalId: String,
        dateOfBirth: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
        assignedDoctor: String,
    ): Patient = error("not used by PatientHistoryViewModel")
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
    fun `load flattens a multi-field entry into one row per field, in field order`() = runTest(dispatcher) {
        val entry = PatientChangeLogEntry(
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "10:00",
            fields = listOf(
                FieldChange("phone", "Telefono de contacto", "1", "2"),
                FieldChange("bloodType", "Grupo sanguineo", "O+", "AB-"),
            ),
        )
        val viewModel = PatientHistoryViewModel(FakePatientRepository(changeLog = listOf(entry)))

        viewModel.uiState.test {
            assertEquals(PatientHistoryUiState(), awaitItem())
            viewModel.load(samplePatient.id)
            val rows = awaitItem().rows
            assertEquals(2, rows.size)
            assertEquals("Telefono de contacto", rows[0].label)
            assertEquals("Grupo sanguineo", rows[1].label)
            assertEquals("apatel", rows[0].changedBy)
            assertEquals("01 Ene 2027", rows[0].fecha)
            assertEquals("10:00", rows[0].hora)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load flattens multiple entries into rows in entry order`() = runTest(dispatcher) {
        val newest = PatientChangeLogEntry(
            changedBy = "apatel",
            fecha = "05 May 2026",
            hora = "11:20",
            fields = listOf(FieldChange("allergies", "Alergias conocidas", "Ninguna", "Penicilina")),
        )
        val oldest = PatientChangeLogEntry(
            changedBy = "mreyes",
            fecha = "13 Abr 2026",
            hora = "08:30",
            fields = listOf(FieldChange("phone", "Telefono de contacto", "987-654-320", "987-654-321")),
        )
        val viewModel = PatientHistoryViewModel(FakePatientRepository(changeLog = listOf(newest, oldest)))

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            val rows = awaitItem().rows
            assertEquals(2, rows.size)
            assertEquals("apatel", rows[0].changedBy)
            assertEquals("mreyes", rows[1].changedBy)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load for a patient with no change log entries produces an empty rows list`() = runTest(dispatcher) {
        val viewModel = PatientHistoryViewModel(FakePatientRepository())

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(emptyList(), awaitItem().rows)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load populates patient alongside rows`() = runTest(dispatcher) {
        val viewModel = PatientHistoryViewModel(FakePatientRepository())

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(samplePatient, awaitItem().patient)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a change logged elsewhere is reflected without re-navigating`() = runTest(dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = PatientHistoryViewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(emptyList(), awaitItem().rows)

            repository.pushChangeLog(
                listOf(
                    PatientChangeLogEntry(
                        changedBy = "apatel",
                        fecha = "01 Ene 2027",
                        hora = "10:00",
                        fields = listOf(FieldChange("phone", "Telefono de contacto", "1", "2")),
                    ),
                ),
            )

            assertEquals(1, awaitItem().rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
