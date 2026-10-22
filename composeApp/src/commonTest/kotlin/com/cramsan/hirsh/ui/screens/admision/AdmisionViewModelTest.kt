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
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.PlaceholderCatalog
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
    currentService = PlaceholderCatalog.service("Medicina General"),
    currentBed = null,
    attendingPhysician = PlaceholderCatalog.physician("Dr. Patel"),
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

    private fun fillRequiredFields(
        viewModel: AdmisionViewModel,
        service: Boolean = true,
        physician: Boolean = true,
    ) {
        if (service) viewModel.onServicioChange(PlaceholderCatalog.service("Medicina General"))
        viewModel.onCamaChange("12")
        if (physician) viewModel.onMedicoResponsableChange(PlaceholderCatalog.physician("Dr. Patel"))
    }

    @Test
    fun `load populates the patient`() = runTest(dispatcher) {
        val viewModel = AdmisionViewModel(FakePatientRepository(), FakeEpisodeRepository())

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
        val viewModel = AdmisionViewModel(FakePatientRepository(emptyList()), FakeEpisodeRepository())

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
        val viewModel = AdmisionViewModel(FakePatientRepository(emptyList()), repository)
        viewModel.load("unknown-patient-id")
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register blocks save when no servicio is chosen`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel, service = false)

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register blocks save when cama is blank`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)
        viewModel.onCamaChange("")

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register blocks save when no medico responsable is chosen`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel, physician = false)

        viewModel.register()

        assertEquals(0, repository.addHospitalizationCalls)
    }

    @Test
    fun `register sends a blank motivo as no admission reason`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
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
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)
        viewModel.onMotivoChange("Sintomas respiratorios")

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.addHospitalizationCalls)
        assertEquals("Sintomas respiratorios", repository.lastAdmissionReason)
        assertEquals(PlaceholderCatalog.service("Medicina General"), repository.lastService)
        assertEquals("12", repository.lastBed?.number)
        assertEquals(PlaceholderCatalog.physician("Dr. Patel"), repository.lastPhysician)
    }

    @Test
    fun `register ignores a second call while a save is already in flight`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository()
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
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
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)

        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.hasActiveHospitalization)
    }

    @Test
    fun `load does not flag a patient whose stays are all closed`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(existing = listOf(stay(EpisodeStatus.CLOSED)))
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)

        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.hasActiveHospitalization)
    }

    @Test
    fun `register refuses a patient with an active stay without calling the repository`() = runTest(dispatcher) {
        val repository = FakeEpisodeRepository(existing = listOf(stay(EpisodeStatus.ACTIVE)))
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
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
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
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
        val viewModel = AdmisionViewModel(FakePatientRepository(), repository)
        viewModel.load(samplePatient.id)
        dispatcher.scheduler.advanceUntilIdle()
        fillRequiredFields(viewModel)

        viewModel.register()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("La cama elegida no esta disponible", viewModel.uiState.value.error)
    }
}
