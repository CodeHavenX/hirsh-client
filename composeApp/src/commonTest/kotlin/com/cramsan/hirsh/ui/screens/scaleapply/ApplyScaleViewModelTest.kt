package com.cramsan.hirsh.ui.screens.scaleapply

import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.AppliedScale
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
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.ScaleDefinition
import com.cramsan.hirsh.model.ScaleOption
import com.cramsan.hirsh.model.ScaleQuestion
import com.cramsan.hirsh.model.ScaleScore
import com.cramsan.hirsh.model.ScaleSeverity
import com.cramsan.hirsh.model.ScaleSummary
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.ClinicalNoteRepository
import com.cramsan.hirsh.repository.EpisodeRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.ScaleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val samplePatient = Patient(
    id = "07c98942-3654-4034-b960-f3265814e214",
    medicalRecordNumber = "HC-00142",
    documentType = DocumentType.NID,
    documentNumber = "45678901",
    fullName = "Maria Gonzalez Huerta",
    birthDate = "14/03/1989",
    sex = Sex.FEMALE,
)

private fun sampleHospitalization(id: String, patientId: String, status: EpisodeStatus = EpisodeStatus.ACTIVE) = Hospitalizacion(
    id = id,
    patientId = patientId,
    currentService = ServiceRef("svc-1", "Psiquiatria"),
    currentBed = BedRef("bed-1", "08"),
    attendingPhysician = UserRef("doc-1", "Dr. Patel"),
    startDate = Instant.parse("2026-04-10T09:00:00Z"),
    endDate = null,
    admissionReason = "Episodio depresivo",
    status = status,
)

private val sampleEvolucion = Evolucion(
    id = "evo1",
    fecha = "18 Jun 2026",
    hora = "10:30",
    medico = "Dr. Hirsh",
    vitals = Vitals(pa = "110/70", fc = "78", fr = "18", temp = "36.6", satO2 = "98", fio2 = "21"),
    diagnosticos = listOf(DiagnosticoCie10("F32.2", "Episodio depresivo grave")),
    subjective = "Refiere mejor descanso.",
    objective = "Afecto menos aplanado.",
    assessment = "",
    plan = "",
    rx = "",
    pronostico = Pronostico.FAVORABLE,
    resultado = EvolucionResultado.FAVORABLE,
    examenes = emptyList(),
    examenesObs = "",
)

private val frequency = (0..3).map { ScaleOption(it, "opt$it") }

private val phq9 = ScaleDefinition(
    id = "phq9",
    code = "PHQ-9",
    name = "PHQ-9",
    stem = null,
    instructions = null,
    questions = (1..9).map { ScaleQuestion("q$it", "Item $it", frequency) },
)

private val gad7 = ScaleDefinition(
    id = "gad7",
    code = "GAD-7",
    name = "GAD-7",
    stem = null,
    instructions = null,
    questions = (1..7).map { ScaleQuestion("g$it", "Item $it", frequency) },
)

private val allAnswered = (1..9).associate { "q$it" to 1 } + ("q9" to 0)

/** Scores like PHQ-9 does: the sum, and the suicide-risk alert whenever q9 is above 0. */
private class FakeScaleRepository(private val definitions: List<ScaleDefinition>) : ScaleRepository {
    val previews = mutableListOf<Map<String, Int>>()
    val applied = mutableListOf<List<Any?>>()
    var applyFailure: Throwable? = null

    override suspend fun getScales(): List<ScaleSummary> = definitions.map { ScaleSummary(it.id, it.code, it.name, null) }

    override suspend fun getScale(scaleId: String): ScaleDefinition = definitions.single { it.id == scaleId }

    override suspend fun previewScore(scaleId: String, answers: Map<String, Int>): ScaleScore {
        previews += answers
        return ScaleScore(
            total = answers.values.sum().toDouble(),
            severity = ScaleSeverity.MILD,
            interpretation = "Depresion leve",
            raisesSuicideAlert = (answers["q9"] ?: 0) > 0,
        )
    }

    override suspend fun applyScale(
        hospId: String,
        documentId: String,
        scaleId: String,
        answers: Map<String, Int>,
        observations: String?,
    ): AppliedScale {
        applyFailure?.let { throw it }
        applied += listOf(hospId, documentId, scaleId, answers, observations)
        return AppliedScale(
            id = "a1",
            documentId = documentId,
            scaleCode = "PHQ-9",
            scaleName = "PHQ-9",
            appliedAt = Instant.parse("2026-06-18T15:00:00Z"),
            appliedBy = "Dr. Hirsh",
            score = previewScore(scaleId, answers),
            observations = observations,
        )
    }

    override fun getAppliedScales(hospId: String): Flow<List<AppliedScale>> = MutableStateFlow(emptyList())
}

private class FakePatientRepository(patients: List<Patient>) : PatientRepository {
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

    override fun getHistoriaClinica(hospId: String): Flow<HistoriaClinica> = MutableStateFlow(HistoriaClinica())

    override fun getEvoluciones(hospId: String): Flow<List<Evolucion>> = evoluciones.map { it[hospId].orEmpty() }

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> =
        evoluciones.map { map -> map[hospId]?.find { it.id == evoId } }

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun signHistoriaClinica(hospId: String) = Unit

    override suspend fun signEvolucion(hospId: String, evoId: String) = Unit

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
}

@OptIn(ExperimentalCoroutinesApi::class)
class ApplyScaleViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.loadedViewModel(
        scales: FakeScaleRepository = FakeScaleRepository(listOf(phq9)),
        stay: Hospitalizacion = sampleHospitalization("h1", samplePatient.id),
        hospId: String = "h1",
    ): ApplyScaleViewModel {
        val viewModel = ApplyScaleViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeEpisodeRepository(listOf(stay)),
            FakeClinicalNoteRepository(mapOf("h1" to listOf(sampleEvolucion))),
            scales,
        )
        viewModel.load(samplePatient.id, hospId, sampleEvolucion.id)
        advanceUntilIdle()
        return viewModel
    }

    private fun ApplyScaleViewModel.answerAll(answers: Map<String, Int>) = answers.forEach { (id, value) -> answer(id, value) }

    @Test
    fun `a single published scale opens straight away`() = runTest(dispatcher) {
        val state = loadedViewModel().uiState.value

        assertEquals(sampleEvolucion, state.evolucion)
        assertEquals(phq9, state.scale)
        assertNull(state.error)
    }

    @Test
    fun `with several scales the clinician picks one first`() = runTest(dispatcher) {
        val viewModel = loadedViewModel(FakeScaleRepository(listOf(phq9, gad7)))
        assertNull(viewModel.uiState.value.scale)
        assertEquals(listOf("PHQ-9", "GAD-7"), viewModel.uiState.value.scales.map { it.code })

        viewModel.selectScale("gad7")
        advanceUntilIdle()

        assertEquals(gad7, viewModel.uiState.value.scale)
    }

    @Test
    fun `changing scale drops the answers given so far`() = runTest(dispatcher) {
        val viewModel = loadedViewModel(FakeScaleRepository(listOf(phq9, gad7)))
        viewModel.selectScale("phq9")
        advanceUntilIdle()
        viewModel.answer("q1", 2)
        advanceUntilIdle()

        viewModel.changeScale()

        val state = viewModel.uiState.value
        assertNull(state.scale)
        assertEquals(emptyMap(), state.answers)
        assertNull(state.score)
    }

    @Test
    fun `an evolucion of a stay that is not this patient's is not found`() = runTest(dispatcher) {
        val scales = FakeScaleRepository(listOf(phq9))
        val state = loadedViewModel(scales, stay = sampleHospitalization("h1", "someone-else")).uiState.value

        assertNull(state.hospitalizacion)
        assertNull(state.evolucion)
        assertNull(state.scale)
    }

    @Test
    fun `quick answers are scored by the server once they settle`() = runTest(dispatcher) {
        val scales = FakeScaleRepository(listOf(phq9))
        val viewModel = loadedViewModel(scales)

        viewModel.answer("q1", 2)
        advanceTimeBy(SCALE_PREVIEW_DEBOUNCE_MILLIS / 2)
        viewModel.answer("q2", 3)
        advanceUntilIdle()

        assertEquals(listOf(mapOf("q1" to 2, "q2" to 3)), scales.previews)
        assertEquals(5.0, viewModel.uiState.value.score?.total)
        assertEquals(2, viewModel.uiState.value.answeredCount)
    }

    @Test
    fun `saving with an unanswered item is refused`() = runTest(dispatcher) {
        val scales = FakeScaleRepository(listOf(phq9))
        val viewModel = loadedViewModel(scales)
        viewModel.answerAll(allAnswered - "q5")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(SCALE_INCOMPLETE_ERROR, viewModel.uiState.value.error)
        assertTrue(scales.applied.isEmpty())
    }

    @Test
    fun `a complete scale without alerts is applied on the evolucion's document`() = runTest(dispatcher) {
        val scales = FakeScaleRepository(listOf(phq9))
        val viewModel = loadedViewModel(scales)
        viewModel.answerAll(allAnswered)
        viewModel.onObservationsChange("Colaboradora")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf<List<Any?>>(listOf("h1", "evo1", "phq9", allAnswered, "Colaboradora")), scales.applied)
        assertTrue(viewModel.uiState.value.saved)
        assertFalse(viewModel.uiState.value.showAlertConfirmation)
    }

    @Test
    fun `answers raising the suicide alert are only saved once confirmed`() = runTest(dispatcher) {
        val scales = FakeScaleRepository(listOf(phq9))
        val viewModel = loadedViewModel(scales)
        viewModel.answerAll(allAnswered + ("q9" to 2))

        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showAlertConfirmation)
        assertTrue(viewModel.uiState.value.score!!.raisesSuicideAlert)
        assertTrue(scales.applied.isEmpty())

        viewModel.confirmSave()
        advanceUntilIdle()

        assertEquals(1, scales.applied.size)
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `reviewing the answers instead keeps the scale unsaved`() = runTest(dispatcher) {
        val scales = FakeScaleRepository(listOf(phq9))
        val viewModel = loadedViewModel(scales)
        viewModel.answerAll(allAnswered + ("q9" to 1))
        viewModel.save()
        advanceUntilIdle()

        viewModel.dismissAlertConfirmation()
        runCurrent()

        assertFalse(viewModel.uiState.value.showAlertConfirmation)
        assertFalse(viewModel.uiState.value.saved)
        assertTrue(scales.applied.isEmpty())
    }

    @Test
    fun `a closed stay takes no scales`() = runTest(dispatcher) {
        val scales = FakeScaleRepository(listOf(phq9))
        val viewModel = loadedViewModel(scales, stay = sampleHospitalization("h1", samplePatient.id, EpisodeStatus.CLOSED))
        assertEquals(SCALE_CLOSED_STAY_ERROR, viewModel.uiState.value.error)
        viewModel.answerAll(allAnswered)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(SCALE_CLOSED_STAY_ERROR, viewModel.uiState.value.error)
        assertTrue(scales.applied.isEmpty())
    }

    @Test
    fun `a stay closed meanwhile explains the refusal`() = runTest(dispatcher) {
        val scales = FakeScaleRepository(listOf(phq9)).apply { applyFailure = ApiException(ApiError.Conflict("closed")) }
        val viewModel = loadedViewModel(scales)
        viewModel.answerAll(allAnswered)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(SCALE_CLOSED_STAY_ERROR, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.saved)
    }

    @Test
    fun `any other failure keeps the answers for another try`() = runTest(dispatcher) {
        val scales = FakeScaleRepository(listOf(phq9)).apply { applyFailure = RuntimeException("Connection refused") }
        val viewModel = loadedViewModel(scales)
        viewModel.answerAll(allAnswered)

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SCALE_SAVE_ERROR, state.error)
        assertEquals(allAnswered, state.answers)
        assertFalse(state.isSaving)
    }
}
