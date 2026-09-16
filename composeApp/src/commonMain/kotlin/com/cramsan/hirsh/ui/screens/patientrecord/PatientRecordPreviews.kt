package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.FieldChange
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewPatients = listOf(
    Patient(
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
    ),
    Patient(
        id = "#00124",
        name = "Olga Karen Santiesteban Bracamonte",
        dateOfBirth = "12/06/1980",
        phone = "944-556-677",
        assignedDoctor = "Dr. Patel",
        lastVisit = "12 Jun 2026",
        bloodType = "O-",
        allergies = "Ninguna",
        nationalId = "40734432",
        sex = Sex.FEMALE,
    ),
)

private val previewChangeLog = mapOf(
    "#00142" to listOf(
        PatientChangeLogEntry(
            changedBy = "apatel",
            fecha = "05 May 2026",
            hora = "11:20",
            fields = listOf(FieldChange("allergies", "Alergias conocidas", "Ninguna", "Penicilina")),
        ),
    ),
)

private fun previewHospitalization(id: String, fechaAlta: String?) = Hospitalizacion(
    id = id,
    patientId = "#00142",
    servicio = "Medicina General",
    cama = "08",
    medicoResponsable = "Dr. Patel",
    fechaIngreso = "10 Abr 2026",
    horaIngreso = "09:00",
    fechaAlta = fechaAlta,
    horaAlta = if (fechaAlta != null) "11:30" else null,
    motivoIngreso = "Tos productiva con fiebre",
    estado = if (fechaAlta != null) EstadoHospitalizacion.ALTA else EstadoHospitalizacion.ACTIVA,
    historiaClinica = HistoriaClinica(
        diagnostico = HcSection(
            complete = true,
            data = Diagnostico(ejeI = "—", ejeII = "—", ejeIII = "Bronquitis aguda (J20.9)", ejeIV = "—", ejeV = "—"),
        ),
        plan = HcSection(
            complete = true,
            data = Plan(
                lugarHospitalizacion = "Medicina General",
                examenesSolicitados = "—",
                psicofarmacos = "—",
                evaluacionesSolicitadas = "—",
            ),
        ),
    ),
    evoluciones = emptyList(),
)

@Preview
@Composable
private fun PatientRecordScreenPreview() {
    HirshTheme {
        PatientRecordScreenContent(
            uiState = PatientRecordUiState(
                isLoading = false,
                patient = previewPatients.first { it.id == "#00142" },
                hospitalizations = listOf(
                    previewHospitalization("h_gonzalez_1", "12 Abr 2026"),
                    previewHospitalization("h_gonzalez_2", "02 Feb 2026"),
                    previewHospitalization("h_gonzalez_3", "15 Nov 2025"),
                ),
                lastChange = previewChangeLog["#00142"]?.firstOrNull(),
            ),
            patientId = "#00142",
            onEditProfile = {},
            onNewHospitalization = {},
            onHospitalizationSelected = {},
            onViewHistory = {},
        )
    }
}

/** #00124 has no hospitalizaciones in this preview's fixtures -- exercises the empty state. */
@Preview
@Composable
private fun PatientRecordScreenEmptyHospitalizationsPreview() {
    HirshTheme {
        PatientRecordScreenContent(
            uiState = PatientRecordUiState(
                isLoading = false,
                patient = previewPatients.first { it.id == "#00124" },
                hospitalizations = emptyList(),
                lastChange = previewChangeLog["#00124"]?.firstOrNull(),
            ),
            patientId = "#00124",
            onEditProfile = {},
            onNewHospitalization = {},
            onHospitalizationSelected = {},
            onViewHistory = {},
        )
    }
}
