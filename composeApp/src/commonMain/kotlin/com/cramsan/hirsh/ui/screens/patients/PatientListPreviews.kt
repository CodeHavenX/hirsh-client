package com.cramsan.hirsh.ui.screens.patients

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.repository.InMemoryPatientRepository
import com.cramsan.hirsh.ui.theme.HirshTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

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
