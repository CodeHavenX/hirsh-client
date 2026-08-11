package com.cramsan.hirsh.ui.screens.patienthistory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.ui.components.DataTable
import com.cramsan.hirsh.ui.components.DataTableColumn
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import org.koin.compose.viewmodel.koinViewModel

private val CellFontSize = 13.sp
private val fieldShape = RoundedCornerShape(HissRadiusDefault)

@Composable
fun PatientHistoryScreen(
    patientId: String,
    onBack: () -> Unit,
    viewModel: PatientHistoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(patientId) {
        viewModel.load(patientId)
    }

    val patient = uiState.patient
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            patient == null -> Text("Paciente no encontrado: $patientId", style = MaterialTheme.typography.bodyMedium)
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "PACIENTES ›",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = HissInk2,
                        )
                        Text(
                            "Historial de cambios · ${patient.name}",
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    OutlinedButton(
                        onClick = onBack,
                        shape = fieldShape,
                        border = BorderStroke(1.5.dp, HissInk),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("history_back_button"),
                    ) {
                        Text("‹ Volver al perfil", fontSize = CellFontSize, fontWeight = FontWeight.Medium, color = HissInk)
                    }
                }

                Text("Cambios registrados", fontSize = 16.sp, fontWeight = FontWeight.Bold)

                if (uiState.rows.isEmpty()) {
                    Text(
                        "Sin cambios registrados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HissInk2,
                    )
                } else {
                    DataTable(columns = historyColumns(), rows = uiState.rows)
                }
            }
        }
    }
}

private fun historyColumns(): List<DataTableColumn<ChangeHistoryRow>> = listOf(
    DataTableColumn(label = "Fecha / Hora", weight = 1.3f) { row ->
        Text("${row.fecha} · ${row.hora}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HissInk2)
    },
    DataTableColumn(label = "Usuario", weight = 0.8f) { row -> Text(row.changedBy, fontSize = CellFontSize) },
    DataTableColumn(label = "Campo", weight = 1f) { row ->
        Text(row.label, fontSize = CellFontSize, fontWeight = FontWeight.SemiBold)
    },
    DataTableColumn(label = "Valor anterior", weight = 1f) { row ->
        Text(row.oldValue.ifBlank { "—" }, fontSize = CellFontSize)
    },
    DataTableColumn(label = "Valor nuevo", weight = 1f) { row ->
        Text(row.newValue.ifBlank { "—" }, fontSize = CellFontSize)
    },
)
