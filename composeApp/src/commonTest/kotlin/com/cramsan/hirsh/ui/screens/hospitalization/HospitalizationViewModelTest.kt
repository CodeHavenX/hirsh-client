package com.cramsan.hirsh.ui.screens.hospitalization

import app.cash.turbine.test
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    servicio = "Medicina General",
    cama = "08",
    medicoResponsable = "Dr. Patel",
    fechaIngreso = "10 Abr 2026",
    horaIngreso = "09:00",
    fechaAlta = null,
    horaAlta = null,
    motivoIngreso = "Tos productiva con fiebre",
    estado = EstadoHospitalizacion.ACTIVA,
    historiaClinica = HistoriaClinica(),
    evoluciones = emptyList(),
)

private class FakePatientRepository(patients: List<Patient>) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override suspend fun refresh() = Unit
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

private class FakeHospitalizationRepository(hospitalizations: List<Hospitalizacion> = emptyList()) :
    HospitalizationRepository {
    private val _hospitalizations = MutableStateFlow(hospitalizations)
    var dischargeCallCount = 0
        private set
    var throwOnDischarge: Exception? = null

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

    override suspend fun discharge(hospId: String, jpaVersion: Long) {
        dischargeCallCount++
        throwOnDischarge?.let { throw it }
        _hospitalizations.update { list ->
            list.map { if (it.id == hospId) it.copy(estado = EstadoHospitalizacion.ALTA) else it }
        }
    }

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

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
            FakeHospitalizationRepository(listOf(hospitalization)),
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
        val viewModel = HospitalizationViewModel(
            FakePatientRepository(emptyList()),
            FakeHospitalizationRepository(),
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
    fun `discharge sets estado to Alta and is reflected without calling load again`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = HospitalizationViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            assertEquals(EstadoHospitalizacion.ACTIVA, awaitItem().hospitalizacion?.estado)

            viewModel.discharge()

            // discharge() flips isDischarging, then mutates the repo, then flips isDischarging
            // back -- several intermediate emissions before the settled ALTA/not-discharging state.
            var settled = awaitItem()
            while (settled.isDischarging || settled.hospitalizacion?.estado != EstadoHospitalizacion.ALTA) {
                settled = awaitItem()
            }
            assertEquals(EstadoHospitalizacion.ALTA, settled.hospitalizacion?.estado)
            assertEquals(false, settled.isDischarging)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discharge ignores a second call while already discharging`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val hospitalizationRepository = FakeHospitalizationRepository(listOf(hospitalization))
        val viewModel = HospitalizationViewModel(FakePatientRepository(listOf(samplePatient)), hospitalizationRepository)
        viewModel.load(samplePatient.id, hospitalization.id)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.discharge()
        viewModel.discharge()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, hospitalizationRepository.dischargeCallCount)
        assertEquals(false, viewModel.uiState.value.isDischarging)
    }

    @Test
    fun `discharge against a stale jpaVersion surfaces a distinct conflict message`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val hospitalizationRepository = FakeHospitalizationRepository(listOf(hospitalization)).apply {
            throwOnDischarge = ApiException(ApiError.Conflict(hospitalization.id))
        }
        val viewModel = HospitalizationViewModel(FakePatientRepository(listOf(samplePatient)), hospitalizationRepository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id, hospitalization.id)
            awaitItem()

            viewModel.discharge()

            var settled = awaitItem()
            while (settled.isDischarging) {
                settled = awaitItem()
            }
            assertEquals(EstadoHospitalizacion.ACTIVA, settled.hospitalizacion?.estado)
            assertEquals(
                "Esta hospitalizacion fue modificada por otro usuario. Recarga la pagina para ver los cambios recientes.",
                settled.error,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discharge against a generic failure surfaces a generic message, not the conflict one`() =
        runTest(dispatcher) {
            val hospitalization = sampleHospitalization("h1", samplePatient.id)
            val hospitalizationRepository = FakeHospitalizationRepository(listOf(hospitalization)).apply {
                throwOnDischarge = IllegalStateException("boom")
            }
            val viewModel =
                HospitalizationViewModel(FakePatientRepository(listOf(samplePatient)), hospitalizationRepository)

            viewModel.uiState.test {
                awaitItem()
                viewModel.load(samplePatient.id, hospitalization.id)
                awaitItem()

                viewModel.discharge()

                var settled = awaitItem()
                while (settled.isDischarging) {
                    settled = awaitItem()
                }
                assertEquals("No se pudo dar de alta al paciente", settled.error)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
