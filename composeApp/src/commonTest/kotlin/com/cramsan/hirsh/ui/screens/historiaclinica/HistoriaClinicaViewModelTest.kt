package com.cramsan.hirsh.ui.screens.historiaclinica

import app.cash.turbine.test
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Plan
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

private fun sampleHospitalization(id: String, patientId: String, historiaClinica: HistoriaClinica = HistoriaClinica()) =
    Hospitalizacion(
        id = id,
        patientId = patientId,
        servicio = "Psiquiatria (UHSMA)",
        cama = "01",
        medicoResponsable = "Dr. Reyes",
        fechaIngreso = "20 May 2026",
        horaIngreso = "14:30",
        fechaAlta = null,
        horaAlta = null,
        motivoIngreso = "Heteroagresividad",
        estado = EstadoHospitalizacion.ACTIVA,
        historiaClinica = historiaClinica,
        evoluciones = emptyList(),
    )

private class FakePatientRepository(patients: List<Patient>) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }
    override fun getChangeLog(patientId: String): Flow<List<PatientChangeLogEntry>> = MutableStateFlow(emptyList())

    override suspend fun updatePatient(
        id: String,
        newValues: Patient,
        changedBy: String,
        fecha: String,
        hora: String,
    ) = Unit

    override suspend fun addPatient(
        name: String,
        nationalId: String,
        dateOfBirth: String,
        phone: String,
        sex: Sex,
        bloodType: String,
        allergies: String,
        assignedDoctor: String,
    ): Patient = error("not used by this test")
}

private class FakeHospitalizationRepository(hospitalizations: List<Hospitalizacion> = emptyList()) :
    HospitalizationRepository {
    private val _hospitalizations = MutableStateFlow(hospitalizations)
    var lastSavedKey: HcSectionKey? = null
        private set
    var lastSavedData: Any? = null
        private set
    var saveCallCount = 0
        private set

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
    ): Hospitalizacion = error("not used by this test")

    override suspend fun discharge(hospId: String) = Unit

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) {
        saveCallCount++
        lastSavedKey = key
        lastSavedData = data
        _hospitalizations.update { list ->
            list.map { hospitalization ->
                if (hospitalization.id != hospId) {
                    hospitalization
                } else {
                    val hc = hospitalization.historiaClinica
                    val updatedHc = when (key) {
                        HcSectionKey.FILIACION -> hc.copy(filiacion = HcSection(complete = true, data = data as Filiacion))
                        HcSectionKey.MOTIVO_INGRESO -> hc.copy(motivoIngreso = HcSection(complete = true, data = data as MotivoIngreso))
                        HcSectionKey.ENFERMEDAD_ACTUAL -> hc.copy(enfermedadActual = HcSection(complete = true, data = data as EnfermedadActual))
                        HcSectionKey.EXAMEN_FISICO -> hc.copy(examenFisico = HcSection(complete = true, data = data as ExamenFisico))
                        HcSectionKey.DIAGNOSTICO -> hc.copy(diagnostico = HcSection(complete = true, data = data as Diagnostico))
                        HcSectionKey.PLAN -> hc.copy(plan = HcSection(complete = true, data = data as Plan))
                        else -> hc
                    }
                    hospitalization.copy(historiaClinica = updatedHc)
                }
            }
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
            FakeHospitalizationRepository(listOf(hospitalization)),
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
        val hospitalization = sampleHospitalization("h1", "#00999")
        val viewModel = HistoriaClinicaViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
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
            FakeHospitalizationRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load("#does-not-exist", "h1")
            val loaded = awaitItem()
            assertNull(loaded.patient)
            assertNull(loaded.hospitalizacion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `field values are seeded from persisted data with no explicit selectSection call`() = runTest(dispatcher) {
        val hc = HistoriaClinica(filiacion = HcSection(complete = true, data = sampleFiliacion()))
        val hospitalization = sampleHospitalization("h1", samplePatient.id, hc)
        val viewModel = HistoriaClinicaViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
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
            FakeHospitalizationRepository(listOf(hospitalization)),
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
        val hospitalization = sampleHospitalization("h1", samplePatient.id, hc)
        val viewModel = HistoriaClinicaViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
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
        val hospitalization = sampleHospitalization("h1", samplePatient.id, hc)
        val viewModel = HistoriaClinicaViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
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
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFieldChange("edad", "13 anos")
        viewModel.onFieldChange("dni", "70083906")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.FILIACION, repository.lastSavedKey)
        val saved = repository.lastSavedData as Filiacion
        assertEquals("13 anos", saved.edad)
        assertEquals("70083906", saved.dni)
    }

    @Test
    fun `save maps fieldValues to EnfermedadActual`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectSection(HcSectionKey.ENFERMEDAD_ACTUAL)
        viewModel.onFieldChange("relato", "Episodio actual...")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.ENFERMEDAD_ACTUAL, repository.lastSavedKey)
        assertEquals("Episodio actual...", (repository.lastSavedData as EnfermedadActual).relato)
    }

    @Test
    fun `save maps fieldValues to ExamenFisico including nested examenRegional`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectSection(HcSectionKey.EXAMEN_FISICO)
        viewModel.onFieldChange("pa", "110/70")
        viewModel.onFieldChange("examenRegional.cabezaCuello", "Normocefalo.")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.EXAMEN_FISICO, repository.lastSavedKey)
        val saved = repository.lastSavedData as ExamenFisico
        assertEquals("110/70", saved.pa)
        assertEquals("Normocefalo.", saved.examenRegional.cabezaCuello)
    }

    @Test
    fun `save maps fieldValues to Diagnostico`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectSection(HcSectionKey.DIAGNOSTICO)
        viewModel.onFieldChange("ejeI", "Psicosis aguda")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.DIAGNOSTICO, repository.lastSavedKey)
        assertEquals("Psicosis aguda", (repository.lastSavedData as Diagnostico).ejeI)
    }

    @Test
    fun `save maps fieldValues to Plan`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectSection(HcSectionKey.PLAN)
        viewModel.onFieldChange("lugarHospitalizacion", "UHSMA")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.PLAN, repository.lastSavedKey)
        assertEquals("UHSMA", (repository.lastSavedData as Plan).lugarHospitalizacion)
    }

    @Test
    fun `save maps Motivo de Ingreso checkbox draft`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectSection(HcSectionKey.MOTIVO_INGRESO)
        viewModel.onMotivoOptionChange(MotivoIngreso(), "psicosis", true)
        viewModel.onMotivoOtrosDetalleChange(MotivoIngreso(psicosis = true), "detalle")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(HcSectionKey.MOTIVO_INGRESO, repository.lastSavedKey)
        val saved = repository.lastSavedData as MotivoIngreso
        assertEquals(true, saved.psicosis)
        assertEquals("detalle", saved.otrosDetalle)
    }

    @Test
    fun `a saved section is reflected live without calling load again`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            val loaded = awaitItem()
            assertEquals(false, loaded.hospitalizacion?.historiaClinica?.filiacion?.complete)

            viewModel.onFieldChange("edad", "13 anos")
            var latest = awaitItem()
            viewModel.save()
            latest = awaitItem()
            while (latest.isSaving) {
                latest = awaitItem()
            }

            assertEquals(true, latest.hospitalizacion?.historiaClinica?.filiacion?.complete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save ignores a second call while already saving`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val repository = FakeHospitalizationRepository(listOf(hospitalization))
        val viewModel = HistoriaClinicaViewModel(FakePatientRepository(listOf(samplePatient)), repository)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFieldChange("edad", "13 anos")
        viewModel.save()
        viewModel.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.saveCallCount)
    }
}
