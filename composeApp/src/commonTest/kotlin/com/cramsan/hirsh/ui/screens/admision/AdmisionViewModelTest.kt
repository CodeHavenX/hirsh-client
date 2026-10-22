package com.cramsan.hirsh.ui.screens.admision

import app.cash.turbine.test
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.AdmissionCatalogRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
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

private val PSIQUIATRIA = ServiceRef("svc-psiq", "Psiquiatria (UHSMA)")
private val MEDICINA = ServiceRef("svc-med", "Medicina General")
private val DR_PATEL = UserRef("usr-patel", "Anita Patel")
private val BED_12 = BedRef("bed-12", "12")
private val BED_03 = BedRef("bed-03", "03")

private class FakeAdmissionCatalogRepository(
    private val bedsByService: Map<String, List<BedRef>> = mapOf(PSIQUIATRIA.id to listOf(BED_12), MEDICINA.id to listOf(BED_03)),
    private val failServices: Boolean = false,
) : AdmissionCatalogRepository {
    val bedRequests = mutableListOf<String>()

    /** Swapped by a test to change what a later bed-list reload returns. */
    var bedsOverride: List<BedRef>? = null

    override suspend fun services(): List<ServiceRef> =
        if (failServices) throw ApiException(ApiError.Unknown("boom")) else listOf(PSIQUIATRIA, MEDICINA)

    override suspend fun availableBeds(serviceId: String): List<BedRef> {
        bedRequests += serviceId
        return bedsOverride ?: bedsByService[serviceId].orEmpty()
    }

    override suspend fun attendingPhysicians(): List<UserRef> = listOf(DR_PATEL)
}

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
    ): Patient = error("not used in this test")

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

private class FakeEpisodeRepository(
    private val existing: List<Hospitalizacion> = emptyList(),
    private val addError: ApiError? = null,
) : EpisodeRepository {
    var addHospitalizationCalls = 0
        private set
    var lastAdmissionReason: String? = null
        private set
    var lastService: ServiceRef? = null
        private set
    var lastBed: BedRef? = null
        private set
    var lastPhysician: UserRef? = null
        private set

    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        MutableStateFlow(existing.filter { it.patientId == patientId })

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        MutableStateFlow(null)

    override suspend fun addHospitalization(
        patientId: String,
        service: ServiceRef,
        bed: BedRef?,
        attendingPhysician: UserRef,
        admissionReason: String?,
    ): Hospitalizacion {
        addHospitalizationCalls++
        addError?.let { throw ApiException(it) }
        lastAdmissionReason = admissionReason
        lastService = service
        lastBed = bed
        lastPhysician = attendingPhysician
        return Hospitalizacion(
            id = "h_new",
            patientId = patientId,
            currentService = service,
            currentBed = bed,
            attendingPhysician = attendingPhysician,
            startDate = Instant.parse("2026-04-10T09:00:00Z"),
            endDate = null,
            admissionReason = admissionReason,
            status = EpisodeStatus.ACTIVE,
        )
    }

    override suspend fun discharge(hospId: String, details: DischargeDetails, jpaVersion: Long) = Unit
}

private fun stay(status: EpisodeStatus) = Hospitalizacion(
    id = "h_existing",
    patientId = samplePatient.id,
    currentService = MEDICINA,
    currentBed = null,
    attendingPhysician = DR_PATEL,
    startDate = Instant.parse("2026-03-01T09:00:00Z"),
    endDate = null,
    admissionReason = null,
    status = status,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AdmisionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        patients: PatientRepository = FakePatientRepository(),
        episodes: EpisodeRepository = FakeEpisodeRepository(),
        catalog: AdmissionCatalogRepository = FakeAdmissionCatalogRepository(),
    ) = AdmisionViewModel(patients, episodes, catalog)

    private fun fillRequiredFields(
        viewModel: AdmisionViewModel,
        service: Boolean = true,
        physician: Boolean = true,
    ) {
        if (service) {
            viewModel.onServicioChange(PSIQUIATRIA)
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.onCamaChange(BED_12)
        }
        if (physician) viewModel.onMedicoResponsableChange(DR_PATEL)
    }

    @Test
    fun `load populates the patient`() = runTest(dispatcher) {
        val viewModel = newViewModel(episodes = FakeEpisodeRepository())

        viewModel.uiState.test {
            skipItems(1)
            viewModel.load(samplePatient.id)
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertEquals(false, loaded.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load leaves patient null when the patient does not exist`() = runTest(dispatcher) {
        val viewModel = newViewModel(patients = FakePatientRepository(emptyList()), episodes = FakeEpisodeRepository())

        viewModel.uiState.test {
            skipItems(1)
            viewModel.load("unknown-patient-id")
            val loaded = awaitItem()
            assertEquals(null, loaded.patient)
            assertEquals(false, loaded.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register does nothing when the patient was not found`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = newViewModel(patients = FakePatientRepository(emptyList()), episodes = repository)
        viewModel.load("unknown-patient-id")
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register blocks save when no servicio is chosen`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = newViewModel(episodes = repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel, service = false)

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register blocks save when no cama is chosen`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = newViewModel(episodes = repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)
        viewModel.onServicioChange(MEDICINA)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register blocks save when no medico responsable is chosen`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = newViewModel(episodes = repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel, physician = false)

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register sends a blank motivo as no admission reason`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = newViewModel(episodes = repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.uiState.test {
            skipItems(1)
            viewModel.register()
            val saving = awaitItem()
            assertEquals(true, saving.isSaving)
            val done = awaitItem()
            assertEquals(false, done.isSaving)
            assertEquals("h_new", done.createdHospitalizationId)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.addHospitalizationCalls)
        assertEquals(null, repository.lastAdmissionReason)
    }

    @Test
    fun `register succeeds with all fields filled`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = newViewModel(episodes = repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)
        viewModel.onMotivoChange("Sintomas respiratorios")

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.addHospitalizationCalls)
        assertEquals("Sintomas respiratorios", repository.lastAdmissionReason)
        assertEquals(PSIQUIATRIA, repository.lastService)
        assertEquals(BED_12, repository.lastBed)
        assertEquals(DR_PATEL, repository.lastPhysician)
    }

    @Test
    fun `register ignores a second call while a save is already in flight`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = newViewModel(episodes = repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.register()
        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.addHospitalizationCalls)
    }

    @Test
    fun `load flags a patient who already has an active stay`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(existing = listOf(stay(EpisodeStatus.ACTIVE)))
        val viewModel = newViewModel(episodes = repository)

        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.hasActiveHospitalization)
    }

    @Test
    fun `load does not flag a patient whose stays are all closed`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(existing = listOf(stay(EpisodeStatus.CLOSED)))
        val viewModel = newViewModel(episodes = repository)

        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.hasActiveHospitalization)
    }

    @Test
    fun `register refuses a patient with an active stay without calling the repository`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(existing = listOf(stay(EpisodeStatus.ACTIVE)))
        val viewModel = newViewModel(episodes = repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
        assertEquals("El paciente ya tiene una hospitalizacion activa", viewModel.uiState.value.error)
    }

    @Test
    fun `register shows the active-stay message for the backend's open-episode conflict`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(addError = ApiError.Conflict("h_opened_elsewhere"))
        val viewModel = newViewModel(episodes = repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("El paciente ya tiene una hospitalizacion activa", viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.isSaving)
    }

    @Test
    fun `register shows a bed message for a conflict without a resource id`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(addError = ApiError.Conflict(""))
        val viewModel = newViewModel(episodes = repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("La cama elegida ya no esta disponible", viewModel.uiState.value.error)
    }

    @Test
    fun `load fills the service and physician pickers`() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(PSIQUIATRIA, MEDICINA), viewModel.uiState.value.services)
        assertEquals(listOf(DR_PATEL), viewModel.uiState.value.physicians)
        assertEquals(emptyList(), viewModel.uiState.value.beds)
    }

    @Test
    fun `load shows an error when the pickers can't be loaded`() = runTest(dispatcher) {
        val viewModel = newViewModel(catalog = FakeAdmissionCatalogRepository(failServices = true))

        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals("No se pudo cargar el formulario de admision", viewModel.uiState.value.error)
    }

    @Test
    fun `picking a service loads its beds and clears a bed from the previous service`() = runTest(dispatcher) {
        val catalog = FakeAdmissionCatalogRepository()
        val viewModel = newViewModel(catalog = catalog)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onServicioChange(PSIQUIATRIA)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(BED_12), viewModel.uiState.value.beds)
        viewModel.onCamaChange(BED_12)

        viewModel.onServicioChange(MEDICINA)
        assertEquals(null, viewModel.uiState.value.bed)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(BED_03), viewModel.uiState.value.beds)
        assertEquals(listOf(PSIQUIATRIA.id, MEDICINA.id), catalog.bedRequests)
    }

    @Test
    fun `a bed taken since the list loaded is cleared and the list reloaded`() = runTest(dispatcher) {
        val catalog = FakeAdmissionCatalogRepository()
        val viewModel = newViewModel(episodes = FakeEpisodeRepository(addError = ApiError.Conflict("")), catalog = catalog)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)
        catalog.bedsOverride = listOf(BED_03)

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.bed)
        assertEquals(listOf(BED_03), state.beds)
        assertEquals("La cama elegida ya no esta disponible", state.error)
    }

    @Test
    fun `the backend's open-episode conflict also blocks further attempts`() = runTest(dispatcher) {
        val viewModel = newViewModel(episodes = FakeEpisodeRepository(addError = ApiError.Conflict("h_opened_elsewhere")))
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.hasActiveHospitalization)
    }
}
