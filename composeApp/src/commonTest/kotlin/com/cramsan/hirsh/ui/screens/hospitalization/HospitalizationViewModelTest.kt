package com.cramsan.hirsh.ui.screens.hospitalization

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
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    currentBed = BedRef("bed-1", "08"),
    attendingPhysician = UserRef("doc-1", "Dr. Patel"),
    startDate = Instant.parse("2026-04-10T09:00:00Z"),
    endDate = null,
    admissionReason = "Tos productiva con fiebre",
    status = EpisodeStatus.ACTIVE,
)

private val discharge = DischargeDetails(DischargeType.MEDICAL, DischargeCondition.IMPROVED, "F32.2")

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
    ): Patient = error("not used by this test")

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
    ): Hospitalizacion = error("not used by this test")

    override suspend fun discharge(hospId: String, details: DischargeDetails, jpaVersion: Long) {
        _hospitalizations.update { list ->
            list.map { if (it.id == hospId) it.copy(status = EpisodeStatus.CLOSED, discharge = details) else it }
        }
    }
}

/** Notes are only read here; [historiasClinicas] is mutable so a test can change them under an open screen. */
private class FakeClinicalNoteRepository(historiasClinicas: Map<String, HistoriaClinica> = emptyMap()) :
    ClinicalNoteRepository {
    val historiasClinicas = MutableStateFlow(historiasClinicas)

    override fun getHistoriaClinica(hospId: String): Flow<HistoriaClinica> =
        historiasClinicas.map { it[hospId] ?: HistoriaClinica() }

    override fun getEvoluciones(hospId: String): Flow<List<Evolucion>> = MutableStateFlow(emptyList())

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> = MutableStateFlow(null)

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun signHistoriaClinica(hospId: String) = Unit

    override suspend fun signEvolucion(hospId: String, evoId: String) = Unit

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
}

@OptIn(ExperimentalCoroutinesApi::class)
class HospitalizationViewModelTest {

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
    fun `load populates patient and hospitalizacion`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = HospitalizationViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(),
        )

        viewModel.uiState.test {
            assertEquals(HospitalizationUiState(), awaitItem())
            viewModel.load(samplePatient.id, hospitalization.id)
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertEquals(hospitalization, loaded.hospitalizacion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when the hospId belongs to a different patient`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", "other-patient-id")
        val viewModel = HospitalizationViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertNull(loaded.hospitalizacion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when the patient does not exist`() = runTest(dispatcher) {
        val viewModel = HospitalizationViewModel(
            FakePatientRepository(emptyList()),
            FakeEpisodeRepository(),
            FakeClinicalNoteRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load("does-not-exist", "h1")
            val loaded = awaitItem()
            assertNull(loaded.patient)
            assertNull(loaded.hospitalizacion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a discharge through the repository reaches the open stay without calling load again`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val episodeRepository = FakeEpisodeRepository(listOf(hospitalization))
        val viewModel = HospitalizationViewModel(
            FakePatientRepository(listOf(samplePatient)),
            episodeRepository,
            FakeClinicalNoteRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            assertEquals(EpisodeStatus.ACTIVE, awaitItem().hospitalizacion?.status)

            episodeRepository.discharge(hospitalization.id, discharge, jpaVersion = 0L)

            val discharged = awaitItem().hospitalizacion
            assertEquals(EpisodeStatus.CLOSED, discharged?.status)
            assertEquals(discharge, discharged?.discharge)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load shows the stay's Historia Clinica, and follows changes to it without calling load again`() =
        runTest(dispatcher) {
            val hospitalization = sampleHospitalization("h1", samplePatient.id)
            val started = HistoriaClinica(filiacion = HcSection(complete = true, data = null))
            val notes = FakeClinicalNoteRepository(mapOf("h1" to started))
            val viewModel = HospitalizationViewModel(
                FakePatientRepository(listOf(samplePatient)),
                FakeEpisodeRepository(listOf(hospitalization)),
                notes,
            )

            viewModel.uiState.test {
                awaitItem()
                viewModel.load(samplePatient.id, hospitalization.id)
                assertEquals(started, awaitItem().historiaClinica)

                notes.historiasClinicas.value = emptyMap()

                assertEquals(HistoriaClinica(), awaitItem().historiaClinica)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed read shows a load error instead of crashing`() = runTest(dispatcher) {
        val patients = FakePatientRepository(listOf(samplePatient)).apply { failure = ApiException(ApiError.Forbidden) }
        val viewModel = HospitalizationViewModel(patients, FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id))), FakeClinicalNoteRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.load(samplePatient.id, "h1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoadError.FORBIDDEN, state.loadError)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `retry loads again after a failure`() = runTest(dispatcher) {
        val patients = FakePatientRepository(listOf(samplePatient)).apply { failure = RuntimeException("Connection refused") }
        val viewModel = HospitalizationViewModel(patients, FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id))), FakeClinicalNoteRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.load(samplePatient.id, "h1")
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
