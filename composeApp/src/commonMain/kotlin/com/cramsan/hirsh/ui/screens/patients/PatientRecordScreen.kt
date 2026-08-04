package com.cramsan.hirsh.ui.screens.patients

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder for the record.html / historia-clinica.html / evolucion.html flows.
 * Wire up as its own feature once the backend exposes hospitalization data.
 */
@Composable
fun PatientRecordScreen(patientId: String) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Ficha del paciente", style = MaterialTheme.typography.headlineSmall)
        Text(patientId, style = MaterialTheme.typography.bodyMedium)
    }
}
