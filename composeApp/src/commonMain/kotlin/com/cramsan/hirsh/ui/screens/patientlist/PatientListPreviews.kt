package com.cramsan.hirsh.ui.screens.patientlist

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.repository.InMemoryPatientRepository
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme

@Preview
@Composable
private fun PatientListScreenPreview() {
    HirshTheme {
        PatientListScreen(
            onPatientSelected = {},
            viewModel = PatientListViewModel(InMemoryPatientRepository()),
        )
    }
}
