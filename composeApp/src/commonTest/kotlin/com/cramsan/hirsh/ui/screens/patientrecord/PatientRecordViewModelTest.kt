package com.cramsan.hirsh.ui.screens.patientrecord

import app.cash.turbine.test
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.FieldChange
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.HospitalizationRepository
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

private fun sampleHospitalization(id: String, patientId: String) = Hospitalizacion(
    id = id,
    patientId = patientId,
    servicio = "Medicina General",
    cama = "01",
    medicoResponsable = "Dr. Patel",
    fechaIngreso = "10 Abr 2026",
    horaIngreso = "09:00",
    fechaAlta = null,
    horaAlta = null,
    motivoIngreso = "Control",
    estado = EstadoHospitalizacion.ACTIVA,
    historiaClinica = HistoriaClinica(),
    evoluciones = emptyList(),
)

private class FakePatientRepository(patients: List<Patient>, changeLog: List<PatientChangeLogEntry> = emptyList()) :
    PatientRepository {
    private val _patients = MutableStateFlow(patients)
    private val _changeLog = MutableStateFlow(changeLog)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }
    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> = _changeLog.asStateFlow()

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
        name: String,
        nationalId: String,
        dateOfBirth: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
        assignedDoctor: String,
    ): Patient {
        val created = Patient(
            id = "#00000",
            name = name,
            dateOfBirth = dateOfBirth,
            phone = phone,
            assignedDoctor = assignedDoctor,
            lastVisit = "—",
            bloodType = bloodType,
            allergies = allergies,
            nationalId = nationalId,
            sex = sex,
        )
        _patients.update { list -> list + created }
        return created
    }
}

private class FakeHospitalizationRepository(hospitalizations: List<Hospitalizacion> = emptyList()) :
    HospitalizationRepository {
    private val _hospitalizations = MutableStateFlow(hospitalizations)

    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        _hospitalizations.map { list -> list.filter { it.patientId == patientId } }

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        _hospitalizations.map { list -> list.find { it.patientId == patientId && it.id == hospId } }

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> =
        _hospitalizations.map { list -> list.find { it.id == hospId }?.evoluciones?.find { it.id == evoId } }

    override suspend fun addHospitalization(
        patientId: String,
        servicio: String,
        cama: String,
        medicoResponsable: String,
        motivoIngreso: String,
    ): Hospitalizacion {
        val created = sampleHospitalization(id = "h_new", patientId = patientId)
        _hospitalizations.update { it + created }
        return created
    }

    override suspend fun discharge(hospId: String) {
        _hospitalizations.update { list ->
            list.map { if (it.id == hospId) it.copy(estado = EstadoHospitalizacion.ALTA) else it }
        }
    }

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
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
        val viewModel = PatientRecordViewModel(FakePatientRepository(listOf(samplePatient)), FakeHospitalizationRepository())

        viewModel.uiState.test {
            assertEquals(PatientRecordUiState(), awaitItem())
            viewModel.load(samplePatient.id)
            assertEquals(PatientRecordUiState(isLoading = false, patient = samplePatient), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load leaves patient null when no match is found`() = runTest(dispatcher) {
        val viewModel = PatientRecordViewModel(FakePatientRepository(emptyList()), FakeHospitalizationRepository())

        viewModel.uiState.test {
            assertEquals(PatientRecordUiState(), awaitItem())
            viewModel.load("#does-not-exist")
            assertEquals(PatientRecordUiState(isLoading = false, patient = null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load populates hospitalizations for a patient that has some`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = PatientRecordViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(listOf(hospitalization), awaitItem().hospitalizations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load leaves hospitalizations empty for a patient with none`() = runTest(dispatcher) {
        val viewModel = PatientRecordViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(emptyList(), awaitItem().hospitalizations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load populates lastChange from the newest change log entry`() = runTest(dispatcher) {
        val entry = PatientChangeLogEntry(
            changedBy = "apatel",
            fecha = "05 May 2026",
            hora = "11:20",
            fields = listOf(FieldChange(field = "phone", label = "Telefono", oldValue = "1", newValue = "2")),
        )
        val viewModel = PatientRecordViewModel(
            FakePatientRepository(listOf(samplePatient), changeLog = listOf(entry)),
            FakeHospitalizationRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(entry, awaitItem().lastChange)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load leaves lastChange null when the change log is empty`() = runTest(dispatcher) {
        val viewModel = PatientRecordViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertNull(awaitItem().lastChange)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a hospitalization mutated elsewhere is reflected without calling load again`() = runTest(dispatcher) {
        val hospitalizationRepository = FakeHospitalizationRepository(
            listOf(sampleHospitalization("h1", samplePatient.id)),
        )
        val viewModel = PatientRecordViewModel(FakePatientRepository(listOf(samplePatient)), hospitalizationRepository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(EstadoHospitalizacion.ACTIVA, awaitItem().hospitalizations.single().estado)

            hospitalizationRepository.discharge("h1")

            assertEquals(EstadoHospitalizacion.ALTA, awaitItem().hospitalizations.single().estado)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
