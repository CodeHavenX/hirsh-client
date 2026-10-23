package com.cramsan.hirsh.ui.screens.evolucionview

import app.cash.turbine.test
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Permission
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.PdfExportRepository
import com.cramsan.hirsh.repository.PdfExportTarget
import com.cramsan.hirsh.repository.PdfFile
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.PdfDownload
import com.cramsan.hirsh.util.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

private fun sampleEvolucion(id: String) = Evolucion(
    id = id,
    fecha = "18 Jun 2026",
    hora = "22:30",
    medico = "Dr. Hirsh",
    vitals = Vitals(pa = "115/72", fc = "80", fr = "20", temp = "37.0", satO2 = "95", fio2 = "24"),
    diagnosticos = listOf(DiagnosticoCie10("B59", "Neumonia por Pneumocystis")),
    subjective = "Paciente refiere notable mejoria de disnea.",
    objective = "PA 115/72, FC 80, FR 20, SatO2 95% con FiO2 24%.",
    assessment = "Buena respuesta a tratamiento antimicrobiano.",
    plan = "Continuar TMP-SMX y corticoides en descenso.",
    rx = "TMP-SMX 15mg/kg/dia EV c/8h",
    pronostico = Pronostico.FAVORABLE,
    resultado = EvolucionResultado.FAVORABLE,
    examenes = emptyList(),
    examenesObs = "",
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

private class FakeClinicalNoteRepository(evoluciones: Map<String, List<Evolucion>> = emptyMap()) :
    ClinicalNoteRepository {
    private val evoluciones = MutableStateFlow(evoluciones)
    var signFailure: Throwable? = null
    var signCallCount = 0
        private set

    override fun getHistoriaClinica(hospId: String): Flow<HistoriaClinica> = MutableStateFlow(HistoriaClinica())

    override fun getEvoluciones(hospId: String): Flow<List<Evolucion>> = evoluciones.map { it[hospId].orEmpty() }

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> =
        evoluciones.map { map -> map[hospId]?.find { it.id == evoId } }

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun signHistoriaClinica(hospId: String) = Unit

    override suspend fun signEvolucion(hospId: String, evoId: String) {
        signCallCount++
        signFailure?.let { throw it }
        evoluciones.update { map ->
            map + (hospId to map[hospId].orEmpty().map { if (it.id == evoId) it.copy(status = NoteStatus.SIGNED) else it })
        }
    }

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
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

private val exporterSession = Session(
    username = "drpatel",
    displayName = "Dr. A. Patel",
    permissions = setOf(Permission.PDF_EXPORT.name),
)

private val signerSession = Session(
    username = "drpatel",
    displayName = "Dr. A. Patel",
    permissions = setOf(Permission.DOCUMENT_SIGN.name),
)

/** Records what was exported; [failure] makes the export throw. */
private class FakePdfExportRepository : PdfExportRepository {
    val targets = mutableListOf<PdfExportTarget>()
    var failure: Exception? = null

    override suspend fun export(target: PdfExportTarget): PdfFile {
        failure?.let { throw it }
        targets += target
        return PdfFile("export.pdf", byteArrayOf(1, 2, 3))
    }
}

private class FakeFileSaver(override val isSupported: Boolean = true) : FileSaver {
    override suspend fun save(filename: String, bytes: ByteArray): String = "/descargas/$filename"
}

@OptIn(ExperimentalCoroutinesApi::class)
class EvolucionViewViewModelTest {

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
    fun `load populates patient, hospitalizacion, and evolucion`() = runTest(dispatcher) {
        val evolucion = sampleEvolucion("evo1")
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(mapOf("h1" to listOf(evolucion))),
            FakeSessionRepository(null),
            FakePdfExportRepository(),
            FakeFileSaver(),
        )

        viewModel.uiState.test {
            assertEquals(EvolucionViewUiState(), awaitItem())
            viewModel.load(samplePatient.id, hospitalization.id, evolucion.id)
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertEquals(hospitalization, loaded.hospitalizacion)
            assertEquals(evolucion, loaded.evolucion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when the hospId belongs to a different patient`() = runTest(dispatcher) {
        val evolucion = sampleEvolucion("evo1")
        val hospitalization = sampleHospitalization("h1", "other-patient-id")
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(mapOf("h1" to listOf(evolucion))),
            FakeSessionRepository(null),
            FakePdfExportRepository(),
            FakeFileSaver(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id, evolucion.id)
            val loaded = awaitItem()
            assertEquals(samplePatient, loaded.patient)
            assertNull(loaded.hospitalizacion)
            assertNull(loaded.evolucion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when the patient does not exist`() = runTest(dispatcher) {
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(emptyList()),
            FakeEpisodeRepository(),
            FakeClinicalNoteRepository(),
            FakeSessionRepository(null),
            FakePdfExportRepository(),
            FakeFileSaver(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load("does-not-exist", "h1", "evo1")
            val loaded = awaitItem()
            assertNull(loaded.patient)
            assertNull(loaded.hospitalizacion)
            assertNull(loaded.evolucion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load produces not-found when evoId does not exist within the hospitalizacion`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1")))),
            FakeSessionRepository(null),
            FakePdfExportRepository(),
            FakeFileSaver(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id, "does-not-exist")
            val loaded = awaitItem()
            assertEquals(hospitalization, loaded.hospitalizacion)
            assertNull(loaded.evolucion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectTab switches the selected tab`() = runTest(dispatcher) {
        val evolucion = sampleEvolucion("evo1")
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(mapOf("h1" to listOf(evolucion))),
            FakeSessionRepository(null),
            FakePdfExportRepository(),
            FakeFileSaver(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id, evolucion.id)
            assertEquals(EvolucionViewTab.EVOLUCION, awaitItem().selectedTab)

            viewModel.selectTab(EvolucionViewTab.EXAMENES)

            assertEquals(EvolucionViewTab.EXAMENES, awaitItem().selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openPrintPreview shows the summary, closePrintPreview hides it`() = runTest(dispatcher) {
        val evolucion = sampleEvolucion("evo1")
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(mapOf("h1" to listOf(evolucion))),
            FakeSessionRepository(null),
            FakePdfExportRepository(),
            FakeFileSaver(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id, evolucion.id)
            assertEquals(false, awaitItem().showPrintPreview)

            viewModel.openPrintPreview()
            assertEquals(true, awaitItem().showPrintPreview)

            viewModel.closePrintPreview()
            assertEquals(false, awaitItem().showPrintPreview)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed read shows a load error instead of crashing`() = runTest(dispatcher) {
        val patients = FakePatientRepository(listOf(samplePatient)).apply { failure = ApiException(ApiError.Forbidden) }
        val viewModel = EvolucionViewViewModel(
            patients,
            FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id))),
            FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1")))),
            FakeSessionRepository(null),
            FakePdfExportRepository(),
            FakeFileSaver(),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.load(samplePatient.id, "h1", "evo1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoadError.FORBIDDEN, state.loadError)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `retry loads again after a failure`() = runTest(dispatcher) {
        val patients = FakePatientRepository(listOf(samplePatient)).apply { failure = RuntimeException("Connection refused") }
        val viewModel = EvolucionViewViewModel(
            patients,
            FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id))),
            FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1")))),
            FakeSessionRepository(null),
            FakePdfExportRepository(),
            FakeFileSaver(),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.load(samplePatient.id, "h1", "evo1")
        advanceUntilIdle()
        assertEquals(LoadError.UNREACHABLE, viewModel.uiState.value.loadError)

        patients.failure = null
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.loadError)
        assertEquals(samplePatient, state.patient)
    }

    @Test
    fun `a signer can sign a draft, which then shows as signed`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1").copy(status = NoteStatus.DRAFT))))
        val viewModel = draftViewModel(notes, signerSession)
        assertEquals(true, viewModel.uiState.value.canSign)

        viewModel.sign()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, notes.signCallCount)
        assertEquals(NoteStatus.SIGNED, state.evolucion?.status)
        assertEquals(false, state.canSign)
        assertEquals(false, state.isSigning)
        assertNull(state.error)
    }

    @Test
    fun `a user without the sign permission cannot sign a draft`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1").copy(status = NoteStatus.DRAFT))))
        val viewModel = draftViewModel(notes, signerSession.copy(permissions = emptySet()))
        assertEquals(false, viewModel.uiState.value.canSign)

        viewModel.sign()
        advanceUntilIdle()

        assertEquals(0, notes.signCallCount)
    }

    @Test
    fun `a signed note offers no signature`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1"))))
        val viewModel = draftViewModel(notes, signerSession)

        assertEquals(false, viewModel.uiState.value.canSign)
    }

    @Test
    fun `a refused signature explains the missing license`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1").copy(status = NoteStatus.DRAFT))))
            .apply { signFailure = ApiException(ApiError.Forbidden) }
        val viewModel = draftViewModel(notes, signerSession)

        viewModel.sign()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EVO_SIGN_FORBIDDEN_ERROR, state.error)
        assertEquals(NoteStatus.DRAFT, state.evolucion?.status)
        assertEquals(false, state.isSigning)
    }

    @Test
    fun `any other signing failure shows a generic error`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1").copy(status = NoteStatus.DRAFT))))
            .apply { signFailure = RuntimeException("Connection refused") }
        val viewModel = draftViewModel(notes, signerSession)

        viewModel.sign()
        advanceUntilIdle()

        assertEquals(EVO_SIGN_ERROR, viewModel.uiState.value.error)
    }

    @Test
    fun `a loaded note offers its plain text for the hospital systems`() = runTest(dispatcher) {
        val evolucion = sampleEvolucion("evo1")
        val viewModel = draftViewModel(FakeClinicalNoteRepository(mapOf("h1" to listOf(evolucion))), signerSession)

        assertEquals(evolucionSisText(samplePatient, evolucion), viewModel.uiState.value.sisText)
    }

    @Test
    fun `a missing note offers nothing to copy`() = runTest(dispatcher) {
        val viewModel = draftViewModel(FakeClinicalNoteRepository(mapOf("h1" to emptyList())), signerSession)

        assertNull(viewModel.uiState.value.sisText)
    }

    @Test
    fun `copying shows a confirmation that goes away on its own`() = runTest(dispatcher) {
        val viewModel = draftViewModel(FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1")))), signerSession)

        viewModel.onCopiedForSis()
        runCurrent()
        assertEquals(true, viewModel.uiState.value.showCopiedConfirmation)

        advanceTimeBy(COPIED_CONFIRMATION_MILLIS + 1)
        assertEquals(false, viewModel.uiState.value.showCopiedConfirmation)
    }

    @Test
    fun `copying again restarts the confirmation`() = runTest(dispatcher) {
        val viewModel = draftViewModel(FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1")))), signerSession)

        viewModel.onCopiedForSis()
        advanceTimeBy(COPIED_CONFIRMATION_MILLIS - 500)
        viewModel.onCopiedForSis()
        advanceTimeBy(1_000)
        assertEquals(true, viewModel.uiState.value.showCopiedConfirmation)
    }

    @Test
    fun `a failed copy explains itself instead of confirming`() = runTest(dispatcher) {
        val viewModel = draftViewModel(FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1")))), signerSession)

        viewModel.onCopyForSisFailed()
        runCurrent()

        assertEquals(false, viewModel.uiState.value.showCopiedConfirmation)
        assertEquals(EVO_COPY_ERROR, viewModel.uiState.value.error)
    }

    @Test
    fun `someone who can apply tests may apply a scale during an active stay`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1"))))
        val viewModel = draftViewModel(notes, signerSession.copy(permissions = setOf(Permission.TEST_WRITE.name)))

        assertEquals(true, viewModel.uiState.value.canApplyScale)
    }

    @Test
    fun `without the test permission no scale is offered`() = runTest(dispatcher) {
        val notes = FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1"))))
        val viewModel = draftViewModel(notes, signerSession)

        assertEquals(false, viewModel.uiState.value.canApplyScale)
    }

    @Test
    fun `an exporter downloads a signed evolucion as a pdf`() = runTest(dispatcher) {
        val pdfs = FakePdfExportRepository()
        val viewModel = draftViewModel(FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1")))), exporterSession, pdfs)
        assertEquals(true, viewModel.uiState.value.canDownloadPdf)

        viewModel.downloadPdf()
        advanceUntilIdle()

        assertEquals(listOf<PdfExportTarget>(PdfExportTarget.Evolucion("h1", "evo1")), pdfs.targets)
        assertEquals(PdfDownload.Saved("/descargas/export.pdf"), viewModel.uiState.value.pdfDownload)
    }

    @Test
    fun `no pdf for a draft, without the permission, or where files can't be saved`() = runTest(dispatcher) {
        val signed = FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1"))))
        val draft = FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion("evo1").copy(status = NoteStatus.DRAFT))))

        assertEquals(false, draftViewModel(draft, exporterSession).uiState.value.canDownloadPdf)
        assertEquals(false, draftViewModel(signed, signerSession).uiState.value.canDownloadPdf)
        assertEquals(false, draftViewModel(signed, exporterSession, fileSaver = FakeFileSaver(isSupported = false)).uiState.value.canDownloadPdf)
    }

    private fun TestScope.draftViewModel(
        notes: FakeClinicalNoteRepository,
        session: Session,
        pdfs: FakePdfExportRepository = FakePdfExportRepository(),
        fileSaver: FakeFileSaver = FakeFileSaver(),
    ): EvolucionViewViewModel {
        val viewModel = EvolucionViewViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id))),
            notes,
            FakeSessionRepository(session),
            pdfs,
            fileSaver,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.load(samplePatient.id, "h1", "evo1")
        advanceUntilIdle()
        return viewModel
    }
}
