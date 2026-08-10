package com.cramsan.hirsh.ui.screens.evolucionnew

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme
import com.cramsan.hirsh.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

private val previewPatient = Patient(
    id = "#00135",
    name = "Jesus Alberto Mendoza Aguilar",
    dateOfBirth = "10/08/1990",
    phone = "955-123-456",
    assignedDoctor = "Dr. Patel",
    lastVisit = "18 Jun 2026",
    bloodType = "B+",
    allergies = "Ninguna",
    nationalId = "70567572",
    sex = Sex.MALE,
)

private val previewHospitalizacion = Hospitalizacion(
    id = "h_mendoza_1",
    patientId = previewPatient.id,
    servicio = "Emergencia - Topico de Medicina",
    cama = "0",
    medicoResponsable = "Dr. Hirsh",
    fechaIngreso = "15 Jun 2026",
    horaIngreso = "12:50",
    fechaAlta = null,
    horaAlta = null,
    motivoIngreso = "Sintomas respiratorios",
    estado = EstadoHospitalizacion.ACTIVA,
    historiaClinica = HistoriaClinica(),
    evoluciones = emptyList(),
)

private class PreviewPatientRepository : PatientRepository {
    private val _patients = MutableStateFlow(listOf(previewPatient))
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
    ): Patient = error("not used by this preview")
}

private class PreviewHospitalizationRepository : HospitalizationRepository {
    private val _hospitalizaciones = MutableStateFlow(listOf(previewHospitalizacion))

    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        _hospitalizaciones.map { list -> list.filter { it.patientId == patientId } }

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        _hospitalizaciones.map { list -> list.find { it.patientId == patientId && it.id == hospId } }

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> =
        _hospitalizaciones.map { list -> list.find { it.id == hospId }?.evoluciones?.find { it.id == evoId } }

    override suspend fun addHospitalization(
        patientId: String,
        servicio: String,
        cama: String,
        medicoResponsable: String,
        motivoIngreso: String,
    ): Hospitalizacion = error("not used by this preview")

    override suspend fun discharge(hospId: String) = Unit

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
}

private object PreviewSessionRepository : SessionRepository {
    private val _session = MutableStateFlow<Session?>(
        Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR),
    )
    override val session: StateFlow<Session?> = _session.asStateFlow()
    override suspend fun login(username: String, password: String): Result<Session> =
        error("not used by this preview")
    override fun logout() = Unit
}

private class PreviewClock : Clock {
    override fun now(): Instant = Instant.parse("2026-06-18T22:30:00Z")
}

private fun previewViewModel(): NuevaEvolucionViewModel {
    val viewModel = NuevaEvolucionViewModel(
        PreviewPatientRepository(),
        PreviewHospitalizationRepository(),
        PreviewSessionRepository,
        PreviewClock(),
    )
    viewModel.load(previewPatient.id, previewHospitalizacion.id)
    return viewModel
}

@Preview
@Composable
private fun NuevaEvolucionScreenPreview() {
    HirshTheme {
        NuevaEvolucionScreen(
            patientId = previewPatient.id,
            hospId = previewHospitalizacion.id,
            onClose = {},
            onDiscarded = {},
            onSaved = {},
            viewModel = previewViewModel(),
            sessionRepository = PreviewSessionRepository,
        )
    }
}

/** Two diagnosis rows and one filled exam row -- exercises the Examenes tab's count badge. */
@Preview
@Composable
private fun NuevaEvolucionScreenFilledPreview() {
    val viewModel = previewViewModel().apply {
        onSubjectiveChange("Paciente refiere notable mejoria de disnea.")
        onObjectiveChange("PA 115/72, FC 80, FR 20, SatO2 95% con FiO2 24%.")
        onDxDescripcionChange(0, "Neumonia por Pneumocystis")
        addDxRow()
        onDxDescripcionChange(1, "VIH Estadio SIDA")
        onPronosticoChange("Favorable")
        onResultadoEvolucionChange("Favorable")
        onExamNombreChange(0, "Gasometria arterial - pO2")
    }
    HirshTheme {
        NuevaEvolucionScreen(
            patientId = previewPatient.id,
            hospId = previewHospitalizacion.id,
            onClose = {},
            onDiscarded = {},
            onSaved = {},
            viewModel = viewModel,
            sessionRepository = PreviewSessionRepository,
        )
    }
}

/** Examenes tab selected. */
@Preview
@Composable
private fun NuevaEvolucionScreenExamenesTabPreview() {
    val viewModel = previewViewModel().apply {
        selectTab(EvolucionTab.EXAMENES)
        onExamNombreChange(0, "Hemoglobina")
    }
    HirshTheme {
        NuevaEvolucionScreen(
            patientId = previewPatient.id,
            hospId = previewHospitalizacion.id,
            onClose = {},
            onDiscarded = {},
            onSaved = {},
            viewModel = viewModel,
            sessionRepository = PreviewSessionRepository,
        )
    }
}
