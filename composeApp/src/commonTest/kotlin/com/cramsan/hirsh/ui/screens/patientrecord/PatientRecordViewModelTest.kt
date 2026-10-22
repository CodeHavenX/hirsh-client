package com.cramsan.hirsh.ui.screens.patientrecord

import kotlin.test.assertNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.ApiError
import app.cash.turbine.test
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DischargeCondition
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DischargeType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.repository.EpisodeRepository
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

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

private fun sampleHospitalization(id: String, patientId: String) = Hospitalizacion(
    id = id,
    patientId = patientId,
    currentService = ServiceRef("svc-1", "Medicina General"),
    currentBed = BedRef("bed-1", "01"),
    attendingPhysician = UserRef("doc-1", "Dr. Patel"),
    startDate = Instant.parse("2026-04-10T09:00:00Z"),
    endDate = null,
    admissionReason = "Control",
    status = EpisodeStatus.ACTIVE,
)

private class FakePatientRepository(patients: List<Patient>) : PatientRepository {
    /** When set, every read throws it -- what a failed network call looks like to a ViewModel. */
    var failure: Throwable? = null
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override suspend fun refresh() {
        failure?.let { throw it }
    }
    override fun getPatient(id: String): Flow<Patient?> = flow {
        failure?.let { throw it }
        emitAll(patients.map { list -> list.find { it.id == id } })
    }

    override suspend fun updatePatient(id: String, newValues: Patient) {
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
    ): Allergy {
        val allergy = Allergy("allergy-new", allergyType, description, severity, observations)
        _patients.update { list -> list.map { if (it.id == patientId) it.copy(allergies = listOf(allergy) + it.allergies) else it } }
        return allergy
    }

    override suspend fun updateAllergy(patientId: String, allergyId: String, severity: Severity?, observations: String): Allergy =
        error("not used by this test")

    override suspend fun deleteAllergy(patientId: String, allergyId: String) = error("not used by this test")
}

private class FakeEpisodeRepository(hospitalizations: List<Hospitalizacion> = emptyList()) :
    EpisodeRepository {
    private val _hospitalizations = MutableStateFlow(hospitalizations)

    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        _hospitalizations.map { list -> list.filter { it.patientId == patientId } }

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        _hospitalizations.map { list -> list.find { it.patientId == patientId && it.id == hospId } }

    override suspend fun addHospitalization(
        patientId: String,
        service: ServiceRef,
        bed: BedRef?,
        attendingPhysician: UserRef,
        admissionReason: String?,
    ): Hospitalizacion {
        val created = sampleHospitalization(id = "h_new", patientId = patientId)
        _hospitalizations.update { it + created }
        return created
    }

    override suspend fun discharge(hospId: String, details: DischargeDetails, jpaVersion: Long) {
        _hospitalizations.update { list ->
            list.map { if (it.id == hospId) it.copy(status = EpisodeStatus.CLOSED) else it }
        }
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
        val viewModel = PatientRecordViewModel(FakePatientRepository(listOf(samplePatient)), FakeEpisodeRepository())

        viewModel.uiState.test {
            assertEquals(PatientRecordUiState(), awaitItem())
            viewModel.load(samplePatient.id)
            assertEquals(PatientRecordUiState(isLoading = false, patient = samplePatient), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load leaves patient null when no match is found`() = runTest(dispatcher) {
        val viewModel = PatientRecordViewModel(FakePatientRepository(emptyList()), FakeEpisodeRepository())

        viewModel.uiState.test {
            assertEquals(PatientRecordUiState(), awaitItem())
            viewModel.load("does-not-exist")
            assertEquals(PatientRecordUiState(isLoading = false, patient = null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load populates hospitalizations for a patient that has some`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = PatientRecordViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
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
            FakeEpisodeRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(emptyList(), awaitItem().hospitalizations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a patient with an active stay can't be admitted again until it's discharged`() = runTest(dispatcher) {
        val episodeRepository = FakeEpisodeRepository(
            listOf(sampleHospitalization("h1", samplePatient.id)),
        )
        val viewModel = PatientRecordViewModel(FakePatientRepository(listOf(samplePatient)), episodeRepository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(false, awaitItem().canAdmit)

            episodeRepository.discharge(
                "h1",
                DischargeDetails(DischargeType.MEDICAL, DischargeCondition.IMPROVED, "F32.2"),
                jpaVersion = 0L,
            )

            assertEquals(true, awaitItem().canAdmit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a hospitalization mutated elsewhere is reflected without calling load again`() = runTest(dispatcher) {
        val episodeRepository = FakeEpisodeRepository(
            listOf(sampleHospitalization("h1", samplePatient.id)),
        )
        val viewModel = PatientRecordViewModel(FakePatientRepository(listOf(samplePatient)), episodeRepository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(EpisodeStatus.ACTIVE, awaitItem().hospitalizations.single().status)

            episodeRepository.discharge(
                "h1",
                DischargeDetails(DischargeType.MEDICAL, DischargeCondition.IMPROVED, "F32.2"),
                jpaVersion = 0L,
            )

            assertEquals(EpisodeStatus.CLOSED, awaitItem().hospitalizations.single().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an allergy added through the repository is reflected without calling load again`() = runTest(dispatcher) {
        val patientRepository = FakePatientRepository(listOf(samplePatient))
        val viewModel = PatientRecordViewModel(patientRepository, FakeEpisodeRepository())

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(listOf("Penicilina"), awaitItem().patient?.allergies?.map { it.description })

            patientRepository.addAllergy(samplePatient.id, AllergyType.FOOD, "Mariscos", Severity.MODERATE, "")

            assertEquals(listOf("Mariscos", "Penicilina"), awaitItem().patient?.allergies?.map { it.description })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed read shows a load error instead of crashing`() = runTest(dispatcher) {
        val patients = FakePatientRepository(listOf(samplePatient)).apply { failure = ApiException(ApiError.Forbidden) }
        val viewModel = PatientRecordViewModel(patients, FakeEpisodeRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.load(samplePatient.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoadError.FORBIDDEN, state.loadError)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `retry loads again after a failure`() = runTest(dispatcher) {
        val patients = FakePatientRepository(listOf(samplePatient)).apply { failure = RuntimeException("Connection refused") }
        val viewModel = PatientRecordViewModel(patients, FakeEpisodeRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.load(samplePatient.id)
        advanceUntilIdle()
        assertEquals(LoadError.UNREACHABLE, viewModel.uiState.value.loadError)

        patients.failure = null
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.loadError)
        assertEquals(samplePatient, state.patient)
    }
}
