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
import com.cramsan.hirsh.model.AccessLogEntry
import com.cramsan.hirsh.model.shortUserId
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.repository.ACCESS_LOG_PAGE_SIZE
import com.cramsan.hirsh.ui.components.DataTable
import com.cramsan.hirsh.ui.components.DataTableColumn
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

    PatientHistoryScreenContent(
        uiState = uiState,
        onBack = onBack,
    )
}

/** All rendering lives here, taking [uiState] as plain data, so `*Previews.kt` never needs a real ViewModel. */
@Composable
internal fun PatientHistoryScreenContent(
    uiState: PatientHistoryUiState,
    onBack: () -> Unit,
) {
    val patient = uiState.patient
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            patient == null -> Text("Paciente no encontrado", style = MaterialTheme.typography.bodyMedium)
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
                            "Registro de accesos · ${patient.fullName}",
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    OutlinedButton(
                        onClick = onBack,
                        shape = fieldShape,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("history_back_button"),
                    ) {
                        Text("‹ Volver al perfil", fontSize = CellFontSize, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Text("Accesos registrados", fontSize = 16.sp, fontWeight = FontWeight.Bold)

                when {
                    uiState.error == AccessLogError.FORBIDDEN -> HistoryMessage(
                        "No tienes permiso para ver el registro de accesos.",
                        "history_forbidden",
                    )
                    uiState.error == AccessLogError.FAILED -> HistoryMessage(
                        "No se pudo cargar el registro de accesos.",
                        "history_error",
                    )
                    uiState.entries.isEmpty() -> HistoryMessage("Sin accesos registrados.", "history_empty")
                    else -> {
                        if (uiState.isTruncated) {
                            Text(
                                "Mostrando los $ACCESS_LOG_PAGE_SIZE accesos mas recientes de ${uiState.totalEntries}.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = HissInk2,
                                modifier = Modifier.testTag("history_truncated_note"),
                            )
                        }
                        DataTable(
                            columns = historyColumns(),
                            rows = uiState.entries,
                            rowTestTag = { entry -> "history_row_${entry.id}" },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMessage(text: String, tag: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = HissInk2, modifier = Modifier.testTag(tag))
}

// "Usuario" shows a shortened account id: names need USER_MANAGE and a real account repository.
private fun historyColumns(): List<DataTableColumn<AccessLogEntry>> = listOf(
    DataTableColumn(label = "Fecha / Hora", weight = 1.3f) { entry ->
        Text(entry.occurredAt, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HissInk2)
    },
    DataTableColumn(label = "Usuario", weight = 0.8f) { entry ->
        Text(entry.shortUserId(), fontFamily = FontFamily.Monospace, fontSize = CellFontSize)
    },
    DataTableColumn(label = "Accion", weight = 1.4f) { entry ->
        Text(entry.action.toDisplayLabel(), fontSize = CellFontSize, fontWeight = FontWeight.SemiBold)
    },
    DataTableColumn(label = "IP", weight = 1f) { entry ->
        Text(entry.sourceIp ?: "—", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HissInk2)
    },
)
