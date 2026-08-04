package com.cramsan.hirsh.ui.screens.patientlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PatientListScreen(
    onPatientSelected: (patientId: String) -> Unit,
    viewModel: PatientListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Pacientes", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(uiState.patients, key = { it.id }) { patient ->
                Card(
                    onClick = { onPatientSelected(patient.id) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(patient.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${patient.id} · ${patient.assignedDoctor} · Ultima visita: ${patient.lastVisit}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
