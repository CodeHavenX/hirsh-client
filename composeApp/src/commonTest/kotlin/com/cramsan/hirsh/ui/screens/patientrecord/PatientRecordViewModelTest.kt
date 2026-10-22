package com.cramsan.hirsh.ui.screens.patientrecord

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
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.assembleFullName
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
    currentBed = BedRef("bed-1", "01"),
    attendingPhysician = UserRef("doc-1", "Dr. Patel"),
    startDate = Instant.parse("2026-04-10T09:00:00Z"),
    endDate = null,
    admissionReason = "Control",
    status = EpisodeStatus.ACTIVE,
    historiaClinica = HistoriaClinica(),
    evoluciones = emptyList(),
)

private class FakePatientRepository(patients: List<Patient>) : PatientRepository {
    private val _patients = MutableStateFlow(patients)
    override val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    override suspend fun refresh() = Unit
    override fun getPatient(id: String): Flow<Patient?> = patients.map { list -> list.find { it.id == id } }

    override suspend fun updatePatient(id: String, newValues: Patient) {
        _patients.update { list -> list.map { if (it.id == id) newValues else it } }
    }

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
    ): Patient {
        val created = Patient(
            id = "new-patient-id",
            medicalRecordNumber = medicalRecordNumber,
            documentType = documentType,
            documentNumber = documentNumber,
            firstName = firstName,
            lastName = lastName,
            secondLastName = secondLastName,
            fullName = assembleFullName(firstName, lastName, secondLastName),
            birthDate = birthDate,
            phone = phone,
            bloodType = bloodType,
            allergies = emptyList(),
            sex = sex,
        )
        _patients.update { list -> list + created }
        return created
    }

    override suspend fun addAllergy(
        patientId: String,
        allergyType: AllergyType,
        description: String,
        severity: Severity?,
        observations: String,
    ): Allergy {
        val allergy = Allergy("allergy-new", allergyType, description, severity, observations)
        _patients.update { list -> list.map { if (it.id == patientId) it.copy(allergies = listOf(allergy) + it.allergies) else it } }
        return allergy
    }

    override suspend fun updateAllergy(patientId: String, allergyId: String, severity: Severity?, observations: String): Allergy =
        error("not used by this test")

    override suspend fun deleteAllergy(patientId: String, allergyId: String) = error("not used by this test")
}

private class FakeHospitalizationRepository(hospitalizations: List<Hospitalizacion> = emptyList()) :
    HospitalizationRepository {
    private val _hospitalizations = MutableStateFlow(hospitalizations)

    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        _hospitalizations.map { list -> list.filter { it.patientId == patientId } }

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        _hospitalizations.map { list -> list.find { it.patientId == patientId && it.id == hospId } }

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> =
        _hospitalizations.map { list -> list.find { it.id == hospId }?.evoluciones?.find { it.id == evoId } }

    override suspend fun addHospitalization(
        patientId: String,
        service: ServiceRef,
        bed: BedRef?,
        attendingPhysician: UserRef,
        admissionReason: String?,
    ): Hospitalizacion {
        val created = sampleHospitalization(id = "h_new", patientId = patientId)
        _hospitalizations.update { it + created }
        return created
    }

    override suspend fun discharge(hospId: String, details: DischargeDetails, jpaVersion: Long) {
        _hospitalizations.update { list ->
            list.map { if (it.id == hospId) it.copy(status = EpisodeStatus.CLOSED) else it }
        }
    }

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
}

@OptIn(ExperimentalCoroutinesApi::class)
class PatientRecordViewModelTest {

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
    fun `load populates the patient when it exists`() = runTest(dispatcher) {
        val viewModel = PatientRecordViewModel(FakePatientRepository(listOf(samplePatient)), FakeHospitalizationRepository())

        viewModel.uiState.test {
            assertEquals(PatientRecordUiState(), awaitItem())
            viewModel.load(samplePatient.id)
            assertEquals(PatientRecordUiState(isLoading = false, patient = samplePatient), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load leaves patient null when no match is found`() = runTest(dispatcher) {
        val viewModel = PatientRecordViewModel(FakePatientRepository(emptyList()), FakeHospitalizationRepository())

        viewModel.uiState.test {
            assertEquals(PatientRecordUiState(), awaitItem())
            viewModel.load("does-not-exist")
            assertEquals(PatientRecordUiState(isLoading = false, patient = null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load populates hospitalizations for a patient that has some`() = runTest(dispatcher) {
        val hospitalization = sampleHospitalization("h1", samplePatient.id)
        val viewModel = PatientRecordViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(listOf(hospitalization)),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(listOf(hospitalization), awaitItem().hospitalizations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load leaves hospitalizations empty for a patient with none`() = runTest(dispatcher) {
        val viewModel = PatientRecordViewModel(
            FakePatientRepository(listOf(samplePatient)),
            FakeHospitalizationRepository(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(emptyList(), awaitItem().hospitalizations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a hospitalization mutated elsewhere is reflected without calling load again`() = runTest(dispatcher) {
        val hospitalizationRepository = FakeHospitalizationRepository(
            listOf(sampleHospitalization("h1", samplePatient.id)),
        )
        val viewModel = PatientRecordViewModel(FakePatientRepository(listOf(samplePatient)), hospitalizationRepository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(EpisodeStatus.ACTIVE, awaitItem().hospitalizations.single().status)

            hospitalizationRepository.discharge(
                "h1",
                DischargeDetails(DischargeType.MEDICAL, DischargeCondition.IMPROVED, "F32.2"),
                jpaVersion = 0L,
            )

            assertEquals(EpisodeStatus.CLOSED, awaitItem().hospitalizations.single().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an allergy added through the repository is reflected without calling load again`() = runTest(dispatcher) {
        val patientRepository = FakePatientRepository(listOf(samplePatient))
        val viewModel = PatientRecordViewModel(patientRepository, FakeHospitalizationRepository())

        viewModel.uiState.test {
            awaitItem()
            viewModel.load(samplePatient.id)
            assertEquals(listOf("Penicilina"), awaitItem().patient?.allergies?.map { it.description })

            patientRepository.addAllergy(samplePatient.id, AllergyType.FOOD, "Mariscos", Severity.MODERATE, "")

            assertEquals(listOf("Mariscos", "Penicilina"), awaitItem().patient?.allergies?.map { it.description })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
