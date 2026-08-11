package com.cramsan.hirsh.ui.screens.admision

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.components.RequiredFieldLabel
import com.cramsan.hirsh.ui.components.SelectField
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import org.koin.compose.viewmodel.koinViewModel

private val servicioOptions = listOf(
    "Emergencia - Topico de Medicina",
    "Medicina General",
    "Psiquiatria (UHSMA)",
    "Pediatria",
    "Gineco-Obstetricia",
    "Cirugia",
)
private val medicoOptions = listOf("Dr. Patel", "Dr. Reyes", "Dr. Lin", "Dr. Hirsh")

@Composable
fun AdmisionScreen(
    patientId: String,
    onAdmitted: (hospitalizationId: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: AdmisionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(patientId) {
        viewModel.load(patientId)
    }
    LaunchedEffect(uiState.createdHospitalizationId) {
        uiState.createdHospitalizationId?.let(onAdmitted)
    }

    val patient = uiState.patient
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).testTag("screen_scroll_container")) {
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            patient == null -> Text("Paciente no encontrado: $patientId", style = MaterialTheme.typography.bodyMedium)
            else -> AdmisionForm(uiState = uiState, patientName = patient.name, viewModel = viewModel, onCancel = onCancel)
        }
    }
}

@Composable
private fun AdmisionForm(
    uiState: AdmisionUiState,
    patientName: String,
    viewModel: AdmisionViewModel,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column {
            Text(
                "PACIENTES › HOSPITALIZACIONES ›",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = HissInk2,
            )
            Text(
                "Nueva hospitalizacion · $patientName",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Datos de ingreso")
                SelectField(
                    label = { RequiredFieldLabel("Servicio") },
                    options = servicioOptions,
                    selected = uiState.servicio,
                    onSelect = viewModel::onServicioChange,
                    testTag = "admision_servicio_field",
                )
                OutlinedTextField(
                    value = uiState.cama,
                    onValueChange = viewModel::onCamaChange,
                    label = { RequiredFieldLabel("Cama") },
                    placeholder = { Text("Ej: 12", fontSize = FieldFontSize) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("admision_cama_field"),
                )
                SelectField(
                    label = { RequiredFieldLabel("Medico responsable") },
                    options = medicoOptions,
                    selected = uiState.medicoResponsable,
                    onSelect = viewModel::onMedicoResponsableChange,
                    testTag = "admision_medico_field",
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Motivo de ingreso")
                OutlinedTextField(
                    value = uiState.motivo,
                    onValueChange = viewModel::onMotivoChange,
                    label = { RequiredFieldLabel("Motivo") },
                    placeholder = {
                        Text(
                            "Ej: Sintomas respiratorios, heteroagresividad, control de hipertension...",
                            fontSize = FieldFontSize,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("admision_motivo_field"),
                )
                Text(
                    "El detalle completo (antecedentes, examen, diagnostico) se documenta en la Historia " +
                        "Clinica de la hospitalizacion.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HissInk2,
                )
            }
        }

        uiState.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !uiState.isSaving,
                shape = fieldShape,
                border = BorderStroke(1.5.dp, HissInk),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("admision_cancel_button"),
            ) {
                Text("Cancelar", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissInk)
            }
            Button(
                onClick = viewModel::register,
                enabled = !uiState.isSaving,
                shape = fieldShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.padding(start = 10.dp).testTag("admision_submit_button"),
            ) {
                Text("Admitir paciente", fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
            }
        }
    }
}
