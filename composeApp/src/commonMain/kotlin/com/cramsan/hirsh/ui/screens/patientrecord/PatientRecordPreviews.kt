package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.repository.InMemoryHospitalizationRepository
import com.cramsan.hirsh.repository.InMemoryPatientRepository
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme
import com.cramsan.hirsh.util.DefaultClock

@Preview
@Composable
private fun PatientRecordScreenPreview() {
    HirshTheme {
        PatientRecordScreen(
            patientId = "#00142",
            onEditProfile = {},
            onNewHospitalization = {},
            onHospitalizationSelected = {},
            onViewHistory = {},
            viewModel = PatientRecordViewModel(
                InMemoryPatientRepository(),
                InMemoryHospitalizationRepository(DefaultClock()),
            ),
        )
    }
}

/** #00124 (Olga Santiesteban) has no hospitalizaciones in the seed data -- exercises the empty state. */
@Preview
@Composable
private fun PatientRecordScreenEmptyHospitalizationsPreview() {
    HirshTheme {
        PatientRecordScreen(
            patientId = "#00124",
            onEditProfile = {},
            onNewHospitalization = {},
            onHospitalizationSelected = {},
            onViewHistory = {},
            viewModel = PatientRecordViewModel(
                InMemoryPatientRepository(),
                InMemoryHospitalizationRepository(DefaultClock()),
            ),
        )
    }
}
