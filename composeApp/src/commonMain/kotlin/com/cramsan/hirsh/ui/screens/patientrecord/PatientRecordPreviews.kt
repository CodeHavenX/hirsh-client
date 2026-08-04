package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.repository.InMemoryPatientRepository
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme

@Preview
@Composable
private fun PatientRecordScreenPreview() {
    HirshTheme {
        PatientRecordScreen(
            patientId = "#00142",
            viewModel = PatientRecordViewModel(InMemoryPatientRepository()),
        )
    }
}
