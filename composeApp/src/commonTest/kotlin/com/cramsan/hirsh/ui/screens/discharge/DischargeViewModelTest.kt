package com.cramsan.hirsh.ui.screens.discharge

import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DischargeCondition
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DischargeType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Icd10Code
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.Icd10Repository
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
    jpaVersion = 3,
)

private val depressionCode = Icd10Code("F32.2", "Episodio depresivo grave sin síntomas psicóticos")

private class FakeIcd10Repository(private val codes: List<Icd10Code> = listOf(depressionCode)) : Icd10Repository {
    val queries = mutableListOf<String>()
    var throwOnSearch: Exception? = null

    override suspend fun search(query: String): List<Icd10Code> {
        queries += query
        throwOnSearch?.let { throw it }
        return codes.filter { it.code.startsWith(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
    }
}

private class FakePatientRepository(patients: List<Patient>) : PatientRepository {
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
    var dischargeCallCount = 0
        private set
    var lastDischarge: Pair<DischargeDetails, Long>? = null
        private set
    var throwOnDischarge: Exception? = null

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
        dischargeCallCount++
        lastDischarge = details to jpaVersion
        throwOnDischarge?.let { throw it }
        _hospitalizations.update { list ->
            list.map { if (it.id == hospId) it.copy(status = EpisodeStatus.CLOSED, discharge = details) else it }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DischargeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        episodeRepository: FakeEpisodeRepository =
            FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id))),
        icd10Repository: FakeIcd10Repository = FakeIcd10Repository(),
    ): DischargeViewModel = DischargeViewModel(FakePatientRepository(listOf(samplePatient)), episodeRepository, icd10Repository)
        .also {
            it.load(samplePatient.id, "h1")
            dispatcher.scheduler.advanceUntilIdle()
        }

    private fun DischargeViewModel.fillRequired() {
        onTypeChange(DischargeType.MEDICAL)
        onConditionChange(DischargeCondition.IMPROVED)
        onDiagnosisSelected(depressionCode)
    }

    @Test
    fun `load populates the patient and the stay`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals(false, state.isLoading)
        assertEquals(samplePatient, state.patient)
        assertEquals("h1", state.hospitalizacion?.id)
    }

    @Test
    fun `diagnosis search waits for the debounce and at least two characters`() = runTest(dispatcher) {
        val icd10 = FakeIcd10Repository()
        val viewModel = viewModel(icd10Repository = icd10)

        viewModel.onDiagnosisQueryChange("F")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList(), icd10.queries, "one character is not searched")

        viewModel.onDiagnosisQueryChange("F3")
        viewModel.onDiagnosisQueryChange("F32")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("F32"), icd10.queries, "only the settled query is searched")
        assertEquals(listOf(depressionCode), viewModel.uiState.value.diagnosisResults)
        assertEquals("F32", viewModel.uiState.value.diagnosisResultsQuery)
    }

    @Test
    fun `results only answer the query once its search finishes`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onDiagnosisQueryChange("F32")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onDiagnosisQueryChange("F99")
        assertEquals("F32", viewModel.uiState.value.diagnosisResultsQuery, "still debouncing: not a 'no match' yet")

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("F99", viewModel.uiState.value.diagnosisResultsQuery)
    }

    @Test
    fun `a failed search shows no results instead of failing the form`() = runTest(dispatcher) {
        val icd10 = FakeIcd10Repository().apply { throwOnSearch = IllegalStateException("offline") }
        val viewModel = viewModel(icd10Repository = icd10)

        viewModel.onDiagnosisQueryChange("F32")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList(), viewModel.uiState.value.diagnosisResults)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `picking a diagnosis clears the search`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onDiagnosisQueryChange("F32")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onDiagnosisSelected(depressionCode)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(depressionCode, state.diagnosis)
        assertEquals("", state.diagnosisQuery)
        assertEquals(emptyList(), state.diagnosisResults)
    }

    @Test
    fun `discharge requires type, condition and diagnosis`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id)))
        val viewModel = viewModel(repository)
        viewModel.onTypeChange(DischargeType.MEDICAL)
        viewModel.onConditionChange(DischargeCondition.IMPROVED)

        viewModel.discharge()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.dischargeCallCount)
        assertEquals("Completa los campos requeridos", viewModel.uiState.value.error)
    }

    @Test
    fun `discharge rejects a next appointment that is not a real dd-MM-yyyy date`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id)))
        val viewModel = viewModel(repository)
        viewModel.fillRequired()

        for (bad in listOf("2026-07-17", "31/02/2026", "17/07/26")) {
            viewModel.onNextAppointmentDateChange(bad)
            viewModel.discharge()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("La proxima cita debe tener el formato dd/mm/aaaa", viewModel.uiState.value.error, bad)
        }
        assertEquals(0, repository.dischargeCallCount)
    }

    @Test
    fun `discharge sends every field with the stay's current version`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id)))
        val viewModel = viewModel(repository)
        viewModel.fillRequired()
        viewModel.onNextAppointmentDateChange(" 17/07/2026 ")
        viewModel.onFollowUpPlanChange("Control en 15 dias")

        viewModel.discharge()
        dispatcher.scheduler.advanceUntilIdle()

        val expected = DischargeDetails(
            type = DischargeType.MEDICAL,
            condition = DischargeCondition.IMPROVED,
            diagnosisIcd10 = "F32.2",
            followUpPlan = "Control en 15 dias",
            nextAppointmentDate = "17/07/2026",
        )
        assertEquals(expected to 3L, repository.lastDischarge)
        assertEquals(true, viewModel.uiState.value.discharged)
        assertEquals(false, viewModel.uiState.value.isSaving)
    }

    @Test
    fun `blank optional fields are sent as absent`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id)))
        val viewModel = viewModel(repository)
        viewModel.fillRequired()
        viewModel.onFollowUpPlanChange("   ")

        viewModel.discharge()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(repository.lastDischarge?.first?.followUpPlan)
        assertNull(repository.lastDischarge?.first?.nextAppointmentDate)
    }

    @Test
    fun `discharge ignores a second call while one is in flight`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id)))
        val viewModel = viewModel(repository)
        viewModel.fillRequired()

        viewModel.discharge()
        viewModel.discharge()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.dischargeCallCount)
    }

    @Test
    fun `a stay that is no longer active is not discharged`() = runTest(dispatcher) {
        val closed = sampleHospitalization("h1", samplePatient.id).copy(status = EpisodeStatus.CLOSED)
        val repository = FakeEpisodeRepository(listOf(closed))
        val viewModel = viewModel(repository)
        viewModel.fillRequired()

        viewModel.discharge()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.dischargeCallCount)
    }

    @Test
    fun `each backend rejection gets its own message`() = runTest(dispatcher) {
        val cases = listOf(
            ApiError.Conflict("") to "No se pudo dar de alta: la hospitalizacion fue modificada por otro usuario, o tiene " +
                "interconsultas pendientes o una nota de ingreso sin firmar. Recarga la pagina e intenta de nuevo.",
            ApiError.NotFound() to "No se encontro la hospitalizacion o el diagnostico CIE-10 elegido",
            ApiError.Unknown("Episode is not active") to "No se pudo dar de alta al paciente",
        )
        for ((error, message) in cases) {
            val repository = FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id)))
                .apply { throwOnDischarge = ApiException(error) }
            val viewModel = viewModel(repository)
            viewModel.fillRequired()

            viewModel.discharge()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(message, viewModel.uiState.value.error, error.toString())
            assertEquals(false, viewModel.uiState.value.isSaving)
            assertEquals(false, viewModel.uiState.value.discharged)
        }
    }
}
