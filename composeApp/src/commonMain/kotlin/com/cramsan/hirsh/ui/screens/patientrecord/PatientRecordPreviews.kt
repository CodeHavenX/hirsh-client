package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PlaceholderCatalog
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme
import kotlin.time.Instant

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
        allergies = listOf(
            Allergy("a1", AllergyType.MEDICATION, "Penicilina", Severity.SEVERE, "Urticaria generalizada tras la primera dosis, 2019."),
            Allergy("a2", AllergyType.FOOD, "Mariscos", severity = null),
        ),
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

private fun previewHospitalization(id: String, startDate: String, endDate: String?) = Hospitalizacion(
    id = id,
    patientId = "07c98942-3654-4034-b960-f3265814e214",
    currentService = PlaceholderCatalog.service("Medicina General"),
    currentBed = PlaceholderCatalog.bed("08"),
    attendingPhysician = PlaceholderCatalog.physician("Dr. Patel"),
    startDate = Instant.parse(startDate),
    endDate = endDate?.let(Instant::parse),
    admissionReason = "Tos productiva con fiebre",
    status = if (endDate != null) EpisodeStatus.CLOSED else EpisodeStatus.ACTIVE,
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
                    previewHospitalization("h_gonzalez_1", "2026-04-10T09:00:00Z", "2026-04-12T11:30:00Z"),
                    previewHospitalization("h_gonzalez_2", "2026-01-31T08:30:00Z", "2026-02-02T10:00:00Z"),
                    previewHospitalization("h_gonzalez_3", "2025-11-14T08:00:00Z", "2025-11-15T09:00:00Z"),
                ),
            ),
            onEditProfile = {},
            onNewHospitalization = {},
            onHospitalizationSelected = {},
            onViewHistory = {},
            onRetry = {},
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
            ),
            onEditProfile = {},
            onNewHospitalization = {},
            onHospitalizationSelected = {},
            onViewHistory = {},
            onRetry = {},
        )
    }
}
