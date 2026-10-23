package com.cramsan.hirsh.ui.screens.scaleapply

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.ScaleQuestion
import com.cramsan.hirsh.model.ScaleScore
import com.cramsan.hirsh.model.ScaleSummary
import com.cramsan.hirsh.model.totalLabel
import com.cramsan.hirsh.ui.components.Chip
import com.cramsan.hirsh.ui.components.EncounterTopBar
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.components.LoadErrorMessage
import com.cramsan.hirsh.ui.components.ScaleSeverityBadge
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.ui.theme.HissWarn
import com.cramsan.hirsh.ui.theme.HissWarnWash
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ApplyScaleScreen(
    patientId: String,
    hospId: String,
    evoId: String,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ApplyScaleViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(patientId, hospId, evoId) {
        viewModel.load(patientId, hospId, evoId)
    }
    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    ApplyScaleScreenContent(
        uiState = uiState,
        onClose = onClose,
        onRetry = viewModel::retry,
        onSelectScale = viewModel::selectScale,
        onChangeScale = viewModel::changeScale,
        onAnswer = viewModel::answer,
        onObservationsChange = viewModel::onObservationsChange,
        onSave = viewModel::save,
        onConfirmSave = viewModel::confirmSave,
        onDismissAlertConfirmation = viewModel::dismissAlertConfirmation,
    )
}

/** All rendering lives here, taking [uiState] as plain data, so `*Previews.kt` never needs a real ViewModel. */
@Composable
internal fun ApplyScaleScreenContent(
    uiState: ApplyScaleUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onSelectScale: (scaleId: String) -> Unit,
    onChangeScale: () -> Unit,
    onAnswer: (questionId: String, value: Int) -> Unit,
    onObservationsChange: (String) -> Unit,
    onSave: () -> Unit,
    onConfirmSave: () -> Unit,
    onDismissAlertConfirmation: () -> Unit,
) {
    val patient = uiState.patient
    val evolucion = uiState.evolucion
    val scale = uiState.scale

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Message("Cargando...")
            uiState.loadError != null -> LoadErrorMessage(uiState.loadError, onRetry = onRetry)
            patient == null -> Message("Paciente no encontrado")
            uiState.hospitalizacion == null -> Message("Hospitalizacion no encontrada")
            evolucion == null -> Message("Evolucion no encontrada")
            else -> {
                EncounterTopBar(
                    title = "Aplicar escala · ${patient.fullName} ${patient.medicalRecordNumber}",
                    onClose = onClose,
                    meta = {
                        Chip("Evolucion ${evolucion.fecha} · ${evolucion.hora}")
                        scale?.let { Chip(it.code) }
                    },
                )
                if (scale != null) {
                    ScoreStrip(
                        score = uiState.score,
                        answered = uiState.answeredCount,
                        total = scale.questions.size,
                    )
                    if (uiState.score?.raisesSuicideAlert == true) SuicideRiskBanner()
                }
                uiState.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).testTag("scale_error"),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
                        .testTag("screen_scroll_container"),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when {
                        uiState.isLoadingScale -> Text("Cargando escala...", style = MaterialTheme.typography.bodyMedium)
                        scale == null -> ScalePicker(uiState.scales, onSelectScale)
                        else -> {
                            scale.stem?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = HissInk2)
                            }
                            scale.instructions?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = HissInk2)
                            }
                            scale.questions.forEachIndexed { index, question ->
                                QuestionCard(
                                    number = index + 1,
                                    question = question,
                                    selected = uiState.answers[question.id],
                                    onAnswer = { value -> onAnswer(question.id, value) },
                                )
                            }
                            OutlinedTextField(
                                value = uiState.observations,
                                onValueChange = onObservationsChange,
                                label = { Text("Observaciones clinicas", fontSize = 12.sp, color = HissInk2) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                                shape = fieldShape,
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth().testTag("scale_observations_field"),
                            )
                            FormActions(
                                canChangeScale = uiState.scales.size > 1,
                                isSaving = uiState.isSaving,
                                onChangeScale = onChangeScale,
                                onSave = onSave,
                            )
                        }
                    }
                }
            }
        }
    }
    if (uiState.showAlertConfirmation) {
        SuicideRiskConfirmation(onConfirm = onConfirmSave, onDismiss = onDismissAlertConfirmation)
    }
}

/** At the end of the form rather than in the top bar, which has no room for actions on a phone. */
@Composable
private fun FormActions(
    canChangeScale: Boolean,
    isSaving: Boolean,
    onChangeScale: () -> Unit,
    onSave: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        if (canChangeScale) {
            OutlinedButton(
                onClick = onChangeScale,
                enabled = !isSaving,
                shape = fieldShape,
                border = BorderStroke(1.5.dp, HissInk),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("scale_change_button"),
            ) {
                Text("Cambiar escala", fontWeight = FontWeight.Medium, color = HissInk)
            }
        }
        Button(
            onClick = onSave,
            enabled = !isSaving,
            shape = fieldShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.testTag("scale_save_button"),
        ) {
            Text("Guardar escala", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
}

@Composable
private fun ScalePicker(scales: List<ScaleSummary>, onSelectScale: (String) -> Unit) {
    FormSectionCaption("Escalas disponibles")
    if (scales.isEmpty()) {
        Text("No hay escalas disponibles.", style = MaterialTheme.typography.bodyMedium, color = HissInk2)
    }
    scales.forEach { scale ->
        Surface(
            onClick = { onSelectScale(scale.id) },
            shape = RoundedCornerShape(HissRadiusDefault),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, HissFaint),
            modifier = Modifier.fillMaxWidth().testTag("scale_pick_${scale.code}"),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(scale.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(scale.code, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HissInk2)
                scale.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HissInk2) }
            }
        }
    }
}

/** The running total, as the server scores it -- the client never adds the answers up itself. */
@Composable
private fun ScoreStrip(score: ScaleScore?, answered: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("scale_score_strip"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("PUNTAJE", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = HissInk2)
            Text(
                score?.totalLabel() ?: "—",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("scale_total"),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            score?.severity?.let { ScaleSeverityBadge(it, modifier = Modifier.testTag("scale_severity")) }
            score?.interpretation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HissInk2) }
        }
        Text(
            "$answered/$total items",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = if (answered == total) HissAccent else HissInk2,
        )
    }
}

@Composable
private fun SuicideRiskBanner() {
    Surface(
        shape = RoundedCornerShape(HissRadiusDefault),
        color = HissWarnWash,
        border = BorderStroke(1.5.dp, HissWarn),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).testTag("scale_suicide_alert"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Riesgo suicida", color = HissWarn, fontWeight = FontWeight.Bold)
            Text(
                "Estas respuestas generan una alerta de riesgo suicida. Al guardar se notificara al medico responsable.",
                color = HissWarn,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun QuestionCard(
    number: Int,
    question: ScaleQuestion,
    selected: Int?,
    onAnswer: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(HissRadiusDefault),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected == null) HissFaint else HissAccent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().testTag("scale_item_${question.id}"),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$number. ${question.text}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    val active = option.value == selected
                    Surface(
                        onClick = { onAnswer(option.value) },
                        shape = RoundedCornerShape(HissRadiusDefault),
                        color = if (active) HissAccentWash else Color.Transparent,
                        border = BorderStroke(1.5.dp, if (active) HissAccent else HissFaint),
                        modifier = Modifier.testTag("scale_option_${question.id}_${option.value}"),
                    ) {
                        Text(
                            "${option.label} (${option.value})",
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) HissAccent else HissInk,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuicideRiskConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alerta de riesgo suicida", fontWeight = FontWeight.Bold, color = HissWarn) },
        text = {
            Text(
                "Estas respuestas indican riesgo suicida. Al guardar la escala se notificara de inmediato al medico " +
                    "responsable. Confirme que las respuestas son correctas.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = HissWarn),
                shape = fieldShape,
                modifier = Modifier.testTag("scale_alert_confirm_button"),
            ) {
                Text("Guardar y notificar", fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("scale_alert_dismiss_button")) {
                Text("Revisar respuestas", color = HissInk)
            }
        },
    )
}
