package com.cramsan.hirsh.ui.screens.patients

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

/**
 * Placeholder for the record.html / historia-clinica.html / evolucion.html flows.
 * Wire up as its own feature once the backend exposes hospitalization data.
 */
@Composable
fun PatientRecordScreen(
    patientId: String,
    viewModel: PatientRecordViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(patientId) {
        viewModel.load(patientId)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Ficha del paciente", style = MaterialTheme.typography.headlineSmall)
        val patient = uiState.patient
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            patient != null -> Text(patient.name, style = MaterialTheme.typography.bodyMedium)
            else -> Text("Paciente no encontrado: $patientId", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
