package com.cramsan.hirsh.ui.screens.evolucionnew

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.ApiError
import app.cash.turbine.test
import com.cramsan.hirsh.repository.DrugRepository
import com.cramsan.hirsh.model.RouteOfAdministration
import com.cramsan.hirsh.model.Prescripcion
import com.cramsan.hirsh.model.Drug
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Permission
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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

    override suspend fun discharge(hospId: String, details: DischargeDetails, jpaVersion: Long) = Unit
}

private class FakeClinicalNoteRepository : ClinicalNoteRepository {
    var lastAddedEvolucion: Evolucion? = null
        private set
    var addEvolucionCallCount = 0
        private set

    override fun getHistoriaClinica(hospId: String): Flow<HistoriaClinica> = MutableStateFlow(HistoriaClinica())

    override fun getEvoluciones(hospId: String): Flow<List<Evolucion>> = MutableStateFlow(emptyList())

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> = MutableStateFlow(null)

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun signHistoriaClinica(hospId: String) = Unit

    val signedEvolucionIds = mutableListOf<String>()
    var signFailure: Throwable? = null

    override suspend fun signEvolucion(hospId: String, evoId: String) {
        signFailure?.let { throw it }
        signedEvolucionIds += evoId
    }

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion {
        addEvolucionCallCount++
        val created = evolucion.copy(id = "evo_new")
        lastAddedEvolucion = created
        return created
    }
}

private class FakeSessionRepository(session: Session?) : SessionRepository {
    private val _session = MutableStateFlow(session)
    override val session: StateFlow<Session?> = _session.asStateFlow()
    override val isRestoring: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override suspend fun login(username: String, password: String): Result<Session> = error("not used by this test")
    override suspend fun restore() = Unit
    override suspend fun logout() = Unit
    override fun forceLogout() = Unit
}

private val sertralina = Drug("d1", "Sertralina", "50 mg", "Tableta", RouteOfAdministration.PO, "Antidepresivos ISRS", isPsychotropic = true)
private val haloperidolIm = Drug("d2", "Haloperidol", "5 mg/mL", "Ampolla", RouteOfAdministration.IM, "Antipsicóticos típicos", isPsychotropic = true)

private class FakeDrugRepository(private val formulary: List<Drug> = listOf(sertralina, haloperidolIm)) : DrugRepository {
    val queries = mutableListOf<String>()

    override suspend fun search(query: String): List<Drug> {
        queries += query
        return formulary.filter { it.genericName.contains(query, ignoreCase = true) }
    }
}

private class FakeClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private val sampleSession = Session(username = "drpatel", displayName = "Dr. A. Patel", roles = listOf("PSYCHIATRIST"))

private val signerSession = sampleSession.copy(permissions = setOf(Permission.DOCUMENT_SIGN.name))

@OptIn(ExperimentalCoroutinesApi::class)
class NuevaEvolucionViewModelTest {

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
        patients: List<Patient> = listOf(samplePatient),
        patientRepository: FakePatientRepository = FakePatientRepository(patients),
        hospitalizations: List<Hospitalizacion> = emptyList(),
        session: Session? = sampleSession,
        notes: FakeClinicalNoteRepository = FakeClinicalNoteRepository(),
        drugs: FakeDrugRepository = FakeDrugRepository(),
    ) = NuevaEvolucionViewModel(
        patientRepository,
        FakeEpisodeRepository(hospitalizations),
        notes,
        drugs,
        FakeSessionRepository(session),
        FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
    )

    @Test
    fun `load populates patient and hospitalizacion`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val vm = viewModel(hospitalizations = listOf(hospitalization))

        vm.uiState.test {
            assertEquals(NuevaEvolucionUiState(), awaitItem())
            vm.load(samplePatient.id, hospitalization.id)
            awaitItem() // isLoading=true, openedFecha/openedHora stamped -- patient/hospitalizacion not fetched yet
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertEquals(hospitalization, loaded.hospitalizacion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load stamps openedFecha and openedHora from the injected Clock, not the live wall clock`() = runTest(dispatcher) {
        val fixedNow = LocalDateTime(2027, 1, 15, 10, 30).toInstant(TimeZone.currentSystemDefault())
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(),
            FakeClinicalNoteRepository(),
            FakeDrugRepository(),
            FakeSessionRepository(sampleSession),
            FakeClock(fixedNow),
        )

        vm.load(samplePatient.id, "h1")

        assertEquals("15 Jan 2027", vm.uiState.value.openedFecha)
        assertEquals("10:30", vm.uiState.value.openedHora)
    }

    @Test
    fun `load produces not-found when the hospId belongs to a different patient`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", "other-patient-id")
        val vm = viewModel(hospitalizations = listOf(hospitalization))

        vm.uiState.test {
            awaitItem()
            vm.load(samplePatient.id, hospitalization.id)
            awaitItem() // isLoading=true, openedFecha/openedHora stamped -- patient/hospitalizacion not fetched yet
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertNull(loaded.hospitalizacion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when the patient does not exist`() = runTest(dispatcher) {
        val vm = viewModel(patients = emptyList())

        vm.uiState.test {
            awaitItem()
            vm.load("does-not-exist", "h1")
            val loaded = awaitItem()
            assertNull(loaded.patient)
            assertNull(loaded.hospitalizacion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun filledState(vm: NuevaEvolucionViewModel) {
        vm.onSubjectiveChange("Refiere mejoria")
        vm.onObjectiveChange("PA 120/80")
        vm.onDxDescripcionChange(0, "Neumonia")
        vm.onPronosticoChange(Pronostico.FAVORABLE.toDisplayLabel())
        vm.onResultadoEvolucionChange(EvolucionResultado.FAVORABLE.toDisplayLabel())
    }

    @Test
    fun `save blocks when subjective is blank`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            notes,
            FakeDrugRepository(),
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)
        vm.onSubjectiveChange("")

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, notes.addEvolucionCallCount)
        assertEquals("Completa los campos requeridos", vm.uiState.value.error)
    }

    @Test
    fun `save blocks when objective is blank`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            notes,
            FakeDrugRepository(),
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)
        vm.onObjectiveChange("")

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, notes.addEvolucionCallCount)
        assertEquals("Completa los campos requeridos", vm.uiState.value.error)
    }

    @Test
    fun `save blocks when no diagnosis has a description`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            notes,
            FakeDrugRepository(),
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)
        vm.onDxDescripcionChange(0, "")

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, notes.addEvolucionCallCount)
        assertEquals("Completa los campos requeridos", vm.uiState.value.error)
    }

    @Test
    fun `save blocks when pronostico is blank`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            notes,
            FakeDrugRepository(),
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)
        vm.onPronosticoChange("")

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, notes.addEvolucionCallCount)
        assertEquals("Completa los campos requeridos", vm.uiState.value.error)
    }

    @Test
    fun `save blocks when resultado evolucion is blank`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            notes,
            FakeDrugRepository(),
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)
        vm.onResultadoEvolucionChange("")

        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, notes.addEvolucionCallCount)
        assertEquals("Completa los campos requeridos", vm.uiState.value.error)
    }

    @Test
    fun `save succeeds, filters blank rows, defaults rx, and sets medico from the session display name`() =
        runTest(dispatcher) {
            val hospitalization = sampleHospitalization("h1", samplePatient.id)
            val repository = FakeEpisodeRepository(listOf(hospitalization))
            val notes = FakeClinicalNoteRepository()
            val vm = NuevaEvolucionViewModel(
                FakePatientRepository(listOf(samplePatient)),
                repository,
                notes,
                FakeDrugRepository(),
                FakeSessionRepository(sampleSession),
                FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
            )
            vm.load(samplePatient.id, hospitalization.id)
            dispatcher.scheduler.advanceUntilIdle()
            filledState(vm)
            vm.addDxRow()
            vm.addExamRow()
            vm.onExamNombreChange(0, "Hemoglobina")

            vm.save()
            dispatcher.scheduler.advanceUntilIdle()

            val created = notes.lastAddedEvolucion
            requireNotNull(created)
            assertEquals(1, created.diagnosticos.size)
            assertEquals("Neumonia", created.diagnosticos.single().descripcion)
            assertEquals(1, created.examenes.size)
            assertEquals("Hemoglobina", created.examenes.single().nombre)
            assertEquals("—", created.rx)
            assertEquals("Dr. A. Patel", created.medico)
            assertEquals(Pronostico.FAVORABLE, created.pronostico)
            assertEquals(EvolucionResultado.FAVORABLE, created.resultado)
            assertEquals("evo_new", vm.uiState.value.createdEvolucionId)
            assertEquals(false, vm.uiState.value.isSaving)
        }

    @Test
    fun `save leaves a draft when the user cannot sign`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository()
        val vm = loadedViewModel(notes, sampleSession)
        filledState(vm)

        vm.save()
        advanceUntilIdle()

        assertEquals(emptyList(), notes.signedEvolucionIds)
        assertEquals("evo_new", vm.uiState.value.createdEvolucionId)
    }

    @Test
    fun `save signs the new note when the user can sign`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository()
        val vm = loadedViewModel(notes, signerSession)
        filledState(vm)

        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("evo_new"), notes.signedEvolucionIds)
        assertEquals("evo_new", vm.uiState.value.createdEvolucionId)
    }

    @Test
    fun `a failed signature still opens the saved draft`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository().apply { signFailure = ApiException(ApiError.Forbidden) }
        val vm = loadedViewModel(notes, signerSession)
        filledState(vm)

        vm.save()
        advanceUntilIdle()

        assertEquals("evo_new", vm.uiState.value.createdEvolucionId)
        assertNull(vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.isSaving)
    }

    private fun TestScope.loadedViewModel(notes: FakeClinicalNoteRepository, session: Session): NuevaEvolucionViewModel {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            notes,
            FakeDrugRepository(),
            FakeSessionRepository(session),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `save ignores a second call while already saving`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val vm = NuevaEvolucionViewModel(
            FakePatientRepository(listOf(samplePatient)),
            repository,
            notes,
            FakeDrugRepository(),
            FakeSessionRepository(sampleSession),
            FakeClock(Instant.parse("2026-06-18T22:30:00Z")),
        )
        vm.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()
        filledState(vm)

        vm.save()
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, notes.addEvolucionCallCount)
    }

    @Test
    fun `removeDxRow never leaves the list empty`() {
        val vm = viewModel()
        vm.removeDxRow(0)

        assertEquals(listOf(DxRow()), vm.uiState.value.diagnosticos)
    }

    @Test
    fun `removeExamRow never leaves the list empty`() {
        val vm = viewModel()
        vm.removeExamRow(0)

        assertEquals(listOf(ExamRow()), vm.uiState.value.examenes)
    }

    @Test
    fun `addDxRow and addExamRow append a new blank row`() {
        val vm = viewModel()
        vm.addDxRow()
        vm.addExamRow()

        assertEquals(2, vm.uiState.value.diagnosticos.size)
        assertEquals(2, vm.uiState.value.examenes.size)
    }

    @Test
    fun `a failed read shows a load error instead of crashing`() = runTest(dispatcher) {
        val patients = FakePatientRepository(listOf(samplePatient)).apply { failure = ApiException(ApiError.Forbidden) }
        val viewModel = viewModel(patientRepository = patients, hospitalizations = listOf(sampleHospitalization("h1", samplePatient.id)))
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
        val viewModel = viewModel(patientRepository = patients, hospitalizations = listOf(sampleHospitalization("h1", samplePatient.id)))
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

    // --- Prescriptions (HCEM-11) ------------------------------------------------------------

    private fun TestScope.loadedViewModel(
        patient: Patient = samplePatient,
        notes: FakeClinicalNoteRepository = FakeClinicalNoteRepository(),
        drugs: FakeDrugRepository = FakeDrugRepository(),
    ): NuevaEvolucionViewModel {
        val vm = viewModel(
            patients = listOf(patient),
            hospitalizations = listOf(sampleHospitalization("h1", patient.id)),
            notes = notes,
            drugs = drugs,
        )
        vm.load(patient.id, "h1")
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `the drug search waits for typing to pause and needs two letters`() = runTest(dispatcher) {
        val drugs = FakeDrugRepository()
        val vm = loadedViewModel(drugs = drugs)
        vm.addRxRow()

        vm.onRxQueryChange(0, "s")
        advanceUntilIdle()
        vm.onRxQueryChange(0, "se")
        vm.onRxQueryChange(0, "sert")
        advanceUntilIdle()

        assertEquals(listOf("sert"), drugs.queries)
        assertEquals(DrugSearch(0, "sert", listOf(sertralina)), vm.uiState.value.drugSearch)
    }

    @Test
    fun `picking a drug closes the search and fills an empty route with the drug's own`() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.addRxRow()
        vm.addRxRow()
        vm.onRxRouteChange(1, RouteOfAdministration.IV)

        vm.onRxQueryChange(0, "halo")
        advanceUntilIdle()
        vm.onRxDrugSelected(0, haloperidolIm)
        vm.onRxDrugSelected(1, haloperidolIm)

        val rows = vm.uiState.value.prescripciones
        assertNull(vm.uiState.value.drugSearch)
        assertEquals(RxRow(drug = haloperidolIm, route = RouteOfAdministration.IM), rows[0])
        assertEquals(RouteOfAdministration.IV, rows[1].route, "a route already chosen is kept")
    }

    @Test
    fun `a drug matching a medication allergy is flagged on its row`() = runTest(dispatcher) {
        val allergic = samplePatient.copy(
            allergies = samplePatient.allergies + Allergy("a2", AllergyType.MEDICATION, "Sertralina", Severity.SEVERE),
        )
        val vm = loadedViewModel(patient = allergic)
        vm.addRxRow()
        vm.addRxRow()

        vm.onRxDrugSelected(0, sertralina)
        vm.onRxDrugSelected(1, haloperidolIm)

        assertEquals(listOf(listOf("a2"), emptyList()), vm.uiState.value.rxAllergies.map { row -> row.map { it.id } })
    }

    @Test
    fun `save refuses a prescription without dose or frequency, but ignores an untouched row`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository()
        val vm = loadedViewModel(notes = notes)
        filledState(vm)
        vm.addRxRow()
        vm.onRxDrugSelected(0, sertralina)
        vm.onRxDoseChange(0, "50 mg")
        vm.addRxRow()

        vm.save()
        advanceUntilIdle()
        assertEquals(RX_INCOMPLETE_ERROR, vm.uiState.value.error)
        assertEquals(0, notes.addEvolucionCallCount)

        vm.onRxFrequencyChange(0, "Cada 24 h")
        vm.save()
        advanceUntilIdle()
        assertEquals(1, notes.addEvolucionCallCount)
    }

    @Test
    fun `save refuses a prescription typed without picking a formulary drug`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository()
        val vm = loadedViewModel(notes = notes)
        filledState(vm)
        vm.addRxRow()
        vm.onRxQueryChange(0, "Sertralina")
        vm.onRxDoseChange(0, "50 mg")
        vm.onRxFrequencyChange(0, "Cada 24 h")

        vm.save()
        advanceUntilIdle()

        assertEquals(RX_INCOMPLETE_ERROR, vm.uiState.value.error)
        assertEquals(0, notes.addEvolucionCallCount)
    }

    @Test
    fun `save sends each prescription with the formulary drug and trimmed fields`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository()
        val vm = loadedViewModel(notes = notes)
        filledState(vm)
        vm.addRxRow()
        vm.onRxDrugSelected(0, sertralina)
        vm.onRxDoseChange(0, " 50 mg ")
        vm.onRxFrequencyChange(0, "Cada 24 h")
        vm.onRxDurationChange(0, "30 dias")
        vm.onRxChange("Dieta blanda")

        vm.save()
        advanceUntilIdle()

        val saved = checkNotNull(notes.lastAddedEvolucion)
        assertEquals(
            listOf(Prescripcion("d1", "Sertralina 50 mg · Tableta", "50 mg", "Cada 24 h", RouteOfAdministration.PO, "30 dias", "")),
            saved.prescripciones,
        )
        assertEquals("Dieta blanda", saved.rx)
    }

    @Test
    fun `removing a row closes an open search, since the rows after it shift`() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.addRxRow()
        vm.addRxRow()
        vm.onRxQueryChange(1, "sert")
        advanceUntilIdle()

        vm.removeRxRow(0)
        advanceUntilIdle()

        assertNull(vm.uiState.value.drugSearch)
        assertEquals(listOf(RxRow(query = "sert")), vm.uiState.value.prescripciones)
    }
}
