package com.cramsan.hirsh.ui.screens.historiaclinica

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
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Plan
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

private fun sampleFiliacion() = Filiacion(
    edad = "13 anos",
    fechaNacimiento = "15/10/2012",
    estadoCivil = "Soltera",
    sexo = "Femenino",
    dni = "70083906",
    gradoInstruccion = "Secundaria",
    ocupacion = "Estudiante",
    lugarNacimiento = "Lima",
    lugarProcedencia = "Villa el Salvador",
    familiarResponsable = "Madre",
    direccion = "Manzana I",
    servicioIngreso = "UHSMA",
)

private fun sampleHospitalization(id: String, patientId: String) =
    Hospitalizacion(
        id = id,
        patientId = patientId,
        currentService = ServiceRef("svc-1", "Psiquiatria (UHSMA)"),
        currentBed = BedRef("bed-1", "01"),
        attendingPhysician = UserRef("doc-1", "Dr. Reyes"),
        startDate = Instant.parse("2026-05-20T14:30:00Z"),
        endDate = null,
        admissionReason = "Heteroagresividad",
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

private class FakeClinicalNoteRepository(historiasClinicas: Map<String, HistoriaClinica> = emptyMap()) :
    ClinicalNoteRepository {
    private val historiasClinicas = MutableStateFlow(historiasClinicas)
    var lastSavedKey: HcSectionKey? = null
        private set
    var lastSavedData: Any? = null
        private set
    var saveCallCount = 0
        private set

    override fun getHistoriaClinica(hospId: String): Flow<HistoriaClinica> =
        historiasClinicas.map { it[hospId] ?: HistoriaClinica() }

    override fun getEvoluciones(hospId: String): Flow<List<Evolucion>> = MutableStateFlow(emptyList())

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> = MutableStateFlow(null)

    override suspend fun signHistoriaClinica(hospId: String) = Unit

    override suspend fun signEvolucion(hospId: String, evoId: String) = Unit

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) {
        saveCallCount++
        lastSavedKey = key
        lastSavedData = data
        historiasClinicas.update { all ->
            val hc = all[hospId] ?: HistoriaClinica()
            val updatedHc = when (key) {
                HcSectionKey.FILIACION -> hc.copy(filiacion = HcSection(complete = true, data = data as Filiacion))
                HcSectionKey.MOTIVO_INGRESO -> hc.copy(motivoIngreso = HcSection(complete = true, data = data as MotivoIngreso))
                HcSectionKey.ENFERMEDAD_ACTUAL -> hc.copy(enfermedadActual = HcSection(complete = true, data = data as EnfermedadActual))
                HcSectionKey.EXAMEN_FISICO -> hc.copy(examenFisico = HcSection(complete = true, data = data as ExamenFisico))
                HcSectionKey.DIAGNOSTICO -> hc.copy(diagnostico = HcSection(complete = true, data = data as Diagnostico))
                HcSectionKey.PLAN -> hc.copy(plan = HcSection(complete = true, data = data as Plan))
                else -> hc
            }
            all + (hospId to updatedHc)
        }
    }

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoriaClinicaViewModelTest {

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
        val viewModel = HistoriaClinicaViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(),
        )

        viewModel.uiState.test {
            assertEquals(HistoriaClinicaUiState(), awaitItem())
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
        val viewModel = HistoriaClinicaViewModel(
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
        val viewModel = HistoriaClinicaViewModel(
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
    fun `field values are seeded from persisted data with no explicit selectSection call`() = runTest(dispatcher) {
        val hc = HistoriaClinica(filiacion = HcSection(complete = true, data = sampleFiliacion()))
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = HistoriaClinicaViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(mapOf("h1" to hc)),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            val loaded = awaitItem()
            assertEquals("13 anos", loaded.fieldValues["edad"])
            assertEquals("70083906", loaded.fieldValues["dni"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `field values are blank when the section has not been saved yet`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = HistoriaClinicaViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            val loaded = awaitItem()
            assertEquals("", loaded.fieldValues["edad"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onFieldChange overrides just that field`() = runTest(dispatcher) {
        val hc = HistoriaClinica(filiacion = HcSection(complete = true, data = sampleFiliacion()))
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = HistoriaClinicaViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(mapOf("h1" to hc)),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            awaitItem()

            viewModel.onFieldChange("edad", "14 anos")

            val updated = awaitItem()
            assertEquals("14 anos", updated.fieldValues["edad"])
            assertEquals("70083906", updated.fieldValues["dni"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectSection clears overrides from the previously active section`() = runTest(dispatcher) {
        val hc = HistoriaClinica(filiacion = HcSection(complete = true, data = sampleFiliacion()))
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = HistoriaClinicaViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(mapOf("h1" to hc)),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            awaitItem()
            viewModel.onFieldChange("edad", "14 anos")
            awaitItem()

            viewModel.selectSection(HcSectionKey.ENFERMEDAD_ACTUAL)
            awaitItem()
            viewModel.selectSection(HcSectionKey.FILIACION)

            val backToFiliacion = awaitItem()
            assertEquals("13 anos", backToFiliacion.fieldValues["edad"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save maps fieldValues to Filiacion`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository, notes)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFieldChange("edad", "13 anos")
        viewModel.onFieldChange("dni", "70083906")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.FILIACION, notes.lastSavedKey)
        val saved = notes.lastSavedData as Filiacion
        assertEquals("13 anos", saved.edad)
        assertEquals("70083906", saved.dni)
    }

    @Test
    fun `save maps fieldValues to EnfermedadActual`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository, notes)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectSection(HcSectionKey.ENFERMEDAD_ACTUAL)
        viewModel.onFieldChange("relato", "Episodio actual...")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.ENFERMEDAD_ACTUAL, notes.lastSavedKey)
        assertEquals("Episodio actual...", (notes.lastSavedData as EnfermedadActual).relato)
    }

    @Test
    fun `save maps fieldValues to ExamenFisico including nested examenRegional`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository, notes)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectSection(HcSectionKey.EXAMEN_FISICO)
        viewModel.onFieldChange("pa", "110/70")
        viewModel.onFieldChange("examenRegional.cabezaCuello", "Normocefalo.")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.EXAMEN_FISICO, notes.lastSavedKey)
        val saved = notes.lastSavedData as ExamenFisico
        assertEquals("110/70", saved.pa)
        assertEquals("Normocefalo.", saved.examenRegional.cabezaCuello)
    }

    @Test
    fun `save maps fieldValues to Diagnostico`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository, notes)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectSection(HcSectionKey.DIAGNOSTICO)
        viewModel.onFieldChange("ejeI", "Psicosis aguda")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.DIAGNOSTICO, notes.lastSavedKey)
        assertEquals("Psicosis aguda", (notes.lastSavedData as Diagnostico).ejeI)
    }

    @Test
    fun `save maps fieldValues to Plan`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository, notes)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectSection(HcSectionKey.PLAN)
        viewModel.onFieldChange("lugarHospitalizacion", "UHSMA")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.PLAN, notes.lastSavedKey)
        assertEquals("UHSMA", (notes.lastSavedData as Plan).lugarHospitalizacion)
    }

    @Test
    fun `save maps Motivo de Ingreso checkbox draft`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository, notes)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectSection(HcSectionKey.MOTIVO_INGRESO)
        viewModel.onMotivoOptionChange(MotivoIngreso(), "psicosis", true)
        viewModel.onMotivoOtrosDetalleChange(MotivoIngreso(psicosis = true), "detalle")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.MOTIVO_INGRESO, notes.lastSavedKey)
        val saved = notes.lastSavedData as MotivoIngreso
        assertEquals(true, saved.psicosis)
        assertEquals("detalle", saved.otrosDetalle)
    }

    @Test
    fun `a saved section is reflected live without calling load again`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository, notes)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            val loaded = awaitItem()
            assertEquals(false, loaded.historiaClinica.filiacion.complete)

            viewModel.onFieldChange("edad", "13 anos")
            var latest = awaitItem()
            viewModel.save()
            latest = awaitItem()
            while (latest.isSaving) {
                latest = awaitItem()
            }

            assertEquals(true, latest.historiaClinica.filiacion.complete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openPrintPreview shows the summary, closePrintPreview hides it`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = HistoriaClinicaViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(hospitalization)),
            FakeClinicalNoteRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            awaitItem()

            viewModel.openPrintPreview()
            assertEquals(true, awaitItem().showPrintPreview)

            viewModel.closePrintPreview()
            assertEquals(false, awaitItem().showPrintPreview)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save ignores a second call while already saving`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeEpisodeRepository(listOf(hospitalization))
        val notes = FakeClinicalNoteRepository()
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository, notes)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFieldChange("edad", "13 anos")
        viewModel.save()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, notes.saveCallCount)
    }

    @Test
    fun `a failed read shows a load error instead of crashing`() = runTest(dispatcher) {
        val patients = FakePatientRepository(listOf(samplePatient)).apply { failure = ApiException(ApiError.Forbidden) }
        val viewModel = HistoriaClinicaViewModel(patients, FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id))), FakeClinicalNoteRepository())
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
        val viewModel = HistoriaClinicaViewModel(patients, FakeEpisodeRepository(listOf(sampleHospitalization("h1", samplePatient.id))), FakeClinicalNoteRepository())
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
