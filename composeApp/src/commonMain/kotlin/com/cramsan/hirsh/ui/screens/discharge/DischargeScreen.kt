package com.cramsan.hirsh.ui.screens.discharge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.cramsan.hirsh.model.DischargeCondition
import com.cramsan.hirsh.model.DischargeType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Icd10Code
import com.cramsan.hirsh.model.serviceName
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.components.LoadErrorMessage
import com.cramsan.hirsh.ui.components.RequiredFieldLabel
import com.cramsan.hirsh.ui.components.SelectField
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import org.koin.compose.viewmodel.koinViewModel

private val typeOptions = DischargeType.entries
private val conditionOptions = DischargeCondition.entries

@Composable
fun DischargeScreen(
    patientId: String,
    hospId: String,
    onDischarged: () -> Unit,
    onCancel: () -> Unit,
    viewModel: DischargeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(patientId, hospId) {
        viewModel.load(patientId, hospId)
    }
    LaunchedEffect(uiState.discharged) {
        if (uiState.discharged) onDischarged()
    }

    DischargeScreenContent(
        uiState = uiState,
        onCancel = onCancel,
        onTypeChange = viewModel::onTypeChange,
        onConditionChange = viewModel::onConditionChange,
        onDiagnosisQueryChange = viewModel::onDiagnosisQueryChange,
        onDiagnosisSelected = viewModel::onDiagnosisSelected,
        onDiagnosisCleared = viewModel::onDiagnosisCleared,
        onNextAppointmentDateChange = viewModel::onNextAppointmentDateChange,
        onFollowUpPlanChange = viewModel::onFollowUpPlanChange,
        onDischarge = viewModel::discharge,
        onRetry = viewModel::retry,
    )
}

/** All rendering lives here, taking [uiState] as plain data, so `*Previews.kt` never needs a real ViewModel. */
@Composable
internal fun DischargeScreenContent(
    uiState: DischargeUiState,
    onCancel: () -> Unit,
    onTypeChange: (DischargeType) -> Unit,
    onConditionChange: (DischargeCondition) -> Unit,
    onDiagnosisQueryChange: (String) -> Unit,
    onDiagnosisSelected: (Icd10Code) -> Unit,
    onDiagnosisCleared: () -> Unit,
    onNextAppointmentDateChange: (String) -> Unit,
    onFollowUpPlanChange: (String) -> Unit,
    onDischarge: () -> Unit,
    onRetry: () -> Unit,
) {
    val patient = uiState.patient
    val hospitalizacion = uiState.hospitalizacion
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).testTag("screen_scroll_container")) {
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            uiState.loadError != null -> LoadErrorMessage(uiState.loadError, onRetry = onRetry)
            patient == null -> Text("Paciente no encontrado", style = MaterialTheme.typography.bodyMedium)
            hospitalizacion == null ->
                Text("Hospitalizacion no encontrada", style = MaterialTheme.typography.bodyMedium)
            hospitalizacion.status != EpisodeStatus.ACTIVE ->
                Text("Esta hospitalizacion ya no esta activa.", style = MaterialTheme.typography.bodyMedium)
            else -> DischargeForm(
                uiState = uiState,
                title = "Alta · ${hospitalizacion.serviceName} · ${patient.fullName}",
                onCancel = onCancel,
                onTypeChange = onTypeChange,
                onConditionChange = onConditionChange,
                onDiagnosisQueryChange = onDiagnosisQueryChange,
                onDiagnosisSelected = onDiagnosisSelected,
                onDiagnosisCleared = onDiagnosisCleared,
                onNextAppointmentDateChange = onNextAppointmentDateChange,
                onFollowUpPlanChange = onFollowUpPlanChange,
                onDischarge = onDischarge,
            )
        }
    }
}

@Composable
private fun DischargeForm(
    uiState: DischargeUiState,
    title: String,
    onCancel: () -> Unit,
    onTypeChange: (DischargeType) -> Unit,
    onConditionChange: (DischargeCondition) -> Unit,
    onDiagnosisQueryChange: (String) -> Unit,
    onDiagnosisSelected: (Icd10Code) -> Unit,
    onDiagnosisCleared: () -> Unit,
    onNextAppointmentDateChange: (String) -> Unit,
    onFollowUpPlanChange: (String) -> Unit,
    onDischarge: () -> Unit,
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
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Datos del alta")
                SelectField(
                    label = { RequiredFieldLabel("Tipo de alta") },
                    options = typeOptions.map { it.toDisplayLabel() },
                    selected = uiState.type?.toDisplayLabel().orEmpty(),
                    onSelect = { label -> typeOptions.firstOrNull { it.toDisplayLabel() == label }?.let(onTypeChange) },
                    testTag = "discharge_type_field",
                )
                SelectField(
                    label = { RequiredFieldLabel("Condicion al alta") },
                    options = conditionOptions.map { it.toDisplayLabel() },
                    selected = uiState.condition?.toDisplayLabel().orEmpty(),
                    onSelect = { label ->
                        conditionOptions.firstOrNull { it.toDisplayLabel() == label }?.let(onConditionChange)
                    },
                    testTag = "discharge_condition_field",
                )
                DiagnosisPicker(
                    query = uiState.diagnosisQuery,
                    results = uiState.diagnosisResults,
                    resultsQuery = uiState.diagnosisResultsQuery,
                    selected = uiState.diagnosis,
                    onQueryChange = onDiagnosisQueryChange,
                    onSelected = onDiagnosisSelected,
                    onCleared = onDiagnosisCleared,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Seguimiento")
                OutlinedTextField(
                    value = uiState.nextAppointmentDate,
                    onValueChange = onNextAppointmentDateChange,
                    label = { Text("Proxima cita", fontSize = 12.sp) },
                    placeholder = { Text("dd/mm/aaaa", fontSize = FieldFontSize) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("discharge_next_appointment_field"),
                )
                OutlinedTextField(
                    value = uiState.followUpPlan,
                    onValueChange = onFollowUpPlanChange,
                    label = { Text("Plan de seguimiento", fontSize = 12.sp) },
                    placeholder = { Text("Ej: Control ambulatorio en 15 dias", fontSize = FieldFontSize) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("discharge_follow_up_field"),
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
                modifier = Modifier.testTag("discharge_cancel_button"),
            ) {
                Text("Cancelar", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissInk)
            }
            Button(
                onClick = onDischarge,
                enabled = !uiState.isSaving,
                shape = fieldShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.padding(start = 10.dp).testTag("discharge_submit_button"),
            ) {
                Text("Dar de alta", fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Typeahead over the ICD-10 catalog: results render inline below the field rather than in a popup,
 * so each one is a plain tagged row an e2e test can click (`discharge_diagnosis_option_<index>`).
 * Once a code is picked it replaces the field until cleared, so only catalogued codes are sent.
 */
@Composable
private fun DiagnosisPicker(
    query: String,
    results: List<Icd10Code>,
    resultsQuery: String?,
    selected: Icd10Code?,
    onQueryChange: (String) -> Unit,
    onSelected: (Icd10Code) -> Unit,
    onCleared: () -> Unit,
) {
    if (selected != null) {
        Surface(
            shape = fieldShape,
            border = BorderStroke(1.dp, HissFaint),
            modifier = Modifier.fillMaxWidth().testTag("discharge_diagnosis_selected"),
        ) {
            // Stacked rather than side by side: a long CIE-10 description beside the button
            // leaves the text a few characters wide at phone width.
            Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp)) {
                RequiredFieldLabel("Diagnostico de alta (CIE-10)")
                Text(
                    "${selected.code} · ${selected.description}",
                    fontSize = FieldFontSize,
                    modifier = Modifier.padding(end = 10.dp),
                )
                TextButton(
                    onClick = onCleared,
                    modifier = Modifier.align(Alignment.End).testTag("discharge_diagnosis_clear_button"),
                ) {
                    Text("Cambiar", fontSize = FieldFontSize)
                }
            }
        }
        return
    }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { RequiredFieldLabel("Diagnostico de alta (CIE-10)") },
            placeholder = { Text("Buscar por codigo o descripcion", fontSize = FieldFontSize) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
            shape = fieldShape,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("discharge_diagnosis_field"),
        )
        if (results.isNotEmpty()) {
            Surface(
                shape = fieldShape,
                border = BorderStroke(1.dp, HissFaint),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column {
                    results.forEachIndexed { index, code ->
                        if (index > 0) HorizontalDivider(color = HissFaint)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelected(code) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("discharge_diagnosis_option_$index"),
                        ) {
                            Text(
                                code.code,
                                fontFamily = FontFamily.Monospace,
                                fontSize = FieldFontSize,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Text(code.description, fontSize = FieldFontSize)
                        }
                    }
                }
            }
        } else if (query.trim().length >= MIN_QUERY_LENGTH && resultsQuery == query.trim()) {
            Text(
                "Sin resultados en el catalogo CIE-10",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = HissInk2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
