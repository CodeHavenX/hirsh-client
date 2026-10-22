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
import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.components.RequiredFieldLabel
import com.cramsan.hirsh.ui.components.SelectField
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import org.koin.compose.viewmodel.koinViewModel

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

    AdmisionScreenContent(
        uiState = uiState,
        onCancel = onCancel,
        onServicioChange = viewModel::onServicioChange,
        onCamaChange = viewModel::onCamaChange,
        onMedicoResponsableChange = viewModel::onMedicoResponsableChange,
        onMotivoChange = viewModel::onMotivoChange,
        onRegister = viewModel::register,
    )
}

/** All rendering lives here, taking [uiState] as plain data, so `*Previews.kt` never needs a real ViewModel. */
@Composable
internal fun AdmisionScreenContent(
    uiState: AdmisionUiState,
    onCancel: () -> Unit,
    onServicioChange: (ServiceRef) -> Unit,
    onCamaChange: (BedRef) -> Unit,
    onMedicoResponsableChange: (UserRef) -> Unit,
    onMotivoChange: (String) -> Unit,
    onRegister: () -> Unit,
) {
    val patient = uiState.patient
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).testTag("screen_scroll_container")) {
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            patient == null -> Text("Paciente no encontrado", style = MaterialTheme.typography.bodyMedium)
            else -> AdmisionForm(
                uiState = uiState,
                patientName = patient.fullName,
                onCancel = onCancel,
                onServicioChange = onServicioChange,
                onCamaChange = onCamaChange,
                onMedicoResponsableChange = onMedicoResponsableChange,
                onMotivoChange = onMotivoChange,
                onRegister = onRegister,
            )
        }
    }
}

@Composable
private fun AdmisionForm(
    uiState: AdmisionUiState,
    patientName: String,
    onCancel: () -> Unit,
    onServicioChange: (ServiceRef) -> Unit,
    onCamaChange: (BedRef) -> Unit,
    onMedicoResponsableChange: (UserRef) -> Unit,
    onMotivoChange: (String) -> Unit,
    onRegister: () -> Unit,
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
                    options = uiState.services.map { it.name },
                    selected = uiState.service?.name.orEmpty(),
                    onSelect = { name -> uiState.services.firstOrNull { it.name == name }?.let(onServicioChange) },
                    testTag = "admision_servicio_field",
                )
                BedPicker(uiState = uiState, onCamaChange = onCamaChange)
                SelectField(
                    label = { RequiredFieldLabel("Medico responsable") },
                    options = uiState.physicians.map { it.fullName },
                    selected = uiState.attendingPhysician?.fullName.orEmpty(),
                    onSelect = { name -> uiState.physicians.firstOrNull { it.fullName == name }?.let(onMedicoResponsableChange) },
                    testTag = "admision_medico_field",
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Motivo de ingreso")
                OutlinedTextField(
                    value = uiState.motivo,
                    onValueChange = onMotivoChange,
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

        when {
            uiState.error != null -> Text(uiState.error, color = MaterialTheme.colorScheme.error)
            uiState.hasActiveHospitalization -> Text(
                "El paciente ya tiene una hospitalizacion activa. Dale de alta antes de registrar otra.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("admision_active_stay_notice"),
            )
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
                onClick = onRegister,
                enabled = !uiState.isSaving && !uiState.hasActiveHospitalization,
                shape = fieldShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.padding(start = 10.dp).testTag("admision_submit_button"),
            ) {
                Text("Admitir paciente", fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * A select rather than the mock's free-text bed number: the backend admits into a bed by id, and
 * only into an available bed of the chosen service.
 */
@Composable
private fun BedPicker(uiState: AdmisionUiState, onCamaChange: (BedRef) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SelectField(
            label = { RequiredFieldLabel("Cama") },
            options = uiState.beds.map { it.number },
            selected = uiState.bed?.number.orEmpty(),
            onSelect = { number -> uiState.beds.firstOrNull { it.number == number }?.let(onCamaChange) },
            testTag = "admision_cama_field",
            enabled = uiState.beds.isNotEmpty(),
        )
        val hint = when {
            uiState.service == null -> "Elige primero el servicio"
            uiState.isLoadingBeds -> "Cargando camas..."
            uiState.beds.isEmpty() -> "Sin camas disponibles en este servicio"
            else -> null
        }
        hint?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = HissInk2) }
    }
}
