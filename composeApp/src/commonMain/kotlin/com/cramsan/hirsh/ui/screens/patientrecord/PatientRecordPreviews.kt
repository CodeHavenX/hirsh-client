package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.FieldChange
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.singleAllergyFromText
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme

private val previewPatients = listOf(
    Patient(
        id = "07c98942-3654-4034-b960-f3265814e214",
        medicalRecordNumber = "HC-00142",
        documentType = DocumentType.NID,
        documentNumber = "45678901",
        fullName = "Maria Gonzalez Huerta",
        birthDate = "14/03/1989",
        phone = "987-654-321",
        bloodType = "O+",
        allergies = singleAllergyFromText("Penicilina"),
        sex = Sex.FEMALE,
    ),
    Patient(
        id = "a7efc7af-d998-43e8-8abd-d07c1155ef9f",
        medicalRecordNumber = "HC-00124",
        documentType = DocumentType.NID,
        documentNumber = "40734432",
        fullName = "Olga Karen Santiesteban Bracamonte",
        birthDate = "12/06/1980",
        phone = "944-556-677",
        bloodType = "O-",
        allergies = emptyList(),
        sex = Sex.FEMALE,
    ),
)

private val previewChangeLog = mapOf(
    "07c98942-3654-4034-b960-f3265814e214" to listOf(
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
    patientId = "07c98942-3654-4034-b960-f3265814e214",
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

@PreviewResponsive
@Composable
private fun PatientRecordScreenPreview() {
    HirshTheme {
        PatientRecordScreenContent(
            uiState = PatientRecordUiState(
                isLoading = false,
                patient = previewPatients.first { it.id == "07c98942-3654-4034-b960-f3265814e214" },
                hospitalizations = listOf(
                    previewHospitalization("h_gonzalez_1", "12 Abr 2026"),
                    previewHospitalization("h_gonzalez_2", "02 Feb 2026"),
                    previewHospitalization("h_gonzalez_3", "15 Nov 2025"),
                ),
                lastChange = previewChangeLog["07c98942-3654-4034-b960-f3265814e214"]?.firstOrNull(),
            ),
            patientId = "07c98942-3654-4034-b960-f3265814e214",
            onEditProfile = {},
            onNewHospitalization = {},
            onHospitalizationSelected = {},
            onViewHistory = {},
        )
    }
}

/** a7efc7af-d998-43e8-8abd-d07c1155ef9f has no hospitalizaciones in this preview's fixtures -- exercises the empty state. */
@PreviewResponsive
@Composable
private fun PatientRecordScreenEmptyHospitalizationsPreview() {
    HirshTheme {
        PatientRecordScreenContent(
            uiState = PatientRecordUiState(
                isLoading = false,
                patient = previewPatients.first { it.id == "a7efc7af-d998-43e8-8abd-d07c1155ef9f" },
                hospitalizations = emptyList(),
                lastChange = previewChangeLog["a7efc7af-d998-43e8-8abd-d07c1155ef9f"]?.firstOrNull(),
            ),
            patientId = "a7efc7af-d998-43e8-8abd-d07c1155ef9f",
            onEditProfile = {},
            onNewHospitalization = {},
            onHospitalizationSelected = {},
            onViewHistory = {},
        )
    }
}
