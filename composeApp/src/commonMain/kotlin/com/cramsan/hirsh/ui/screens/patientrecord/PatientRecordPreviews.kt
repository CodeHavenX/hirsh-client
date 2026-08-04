package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.repository.InMemoryPatientRepository
import com.cramsan.hirsh.ui.theme.HirshTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

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
