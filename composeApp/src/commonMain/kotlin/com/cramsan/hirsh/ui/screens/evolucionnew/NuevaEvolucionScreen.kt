package com.cramsan.hirsh.ui.screens.evolucionnew

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.components.BadgeTone
import com.cramsan.hirsh.ui.components.Chip
import com.cramsan.hirsh.ui.components.EncounterTopBar
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.components.RequiredFieldLabel
import com.cramsan.hirsh.ui.components.SelectField
import com.cramsan.hirsh.ui.components.StatusBadge
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.ui.theme.HissWarn
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val examTipoOptions = listOf("Laboratorio", "Imagenologia", "Otro")

@Composable
fun NuevaEvolucionScreen(
    patientId: String,
    hospId: String,
    onClose: () -> Unit,
    onDiscarded: () -> Unit,
    onSaved: (evoId: String) -> Unit,
    viewModel: NuevaEvolucionViewModel = koinViewModel(),
    sessionRepository: SessionRepository = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by sessionRepository.session.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(patientId, hospId) {
        viewModel.load(patientId, hospId)
    }
    LaunchedEffect(uiState.createdEvolucionId) {
        uiState.createdEvolucionId?.let(onSaved)
    }

    NuevaEvolucionScreenContent(
        uiState = uiState,
        patientId = patientId,
        hospId = hospId,
        sessionDisplayName = session?.displayName.orEmpty(),
        showDiscardDialog = showDiscardDialog,
        onClose = onClose,
        onRequestDiscard = { showDiscardDialog = true },
        onDismissDiscardDialog = { showDiscardDialog = false },
        onConfirmDiscard = {
            showDiscardDialog = false
            onDiscarded()
        },
        onSave = viewModel::save,
        onSelectTab = viewModel::selectTab,
        onSubjectiveChange = viewModel::onSubjectiveChange,
        onObjectiveChange = viewModel::onObjectiveChange,
        onAssessmentChange = viewModel::onAssessmentChange,
        onPlanChange = viewModel::onPlanChange,
        onRxChange = viewModel::onRxChange,
        onPaChange = viewModel::onPaChange,
        onFcChange = viewModel::onFcChange,
        onFrChange = viewModel::onFrChange,
        onTempChange = viewModel::onTempChange,
        onSatO2Change = viewModel::onSatO2Change,
        onFio2Change = viewModel::onFio2Change,
        onPronosticoChange = viewModel::onPronosticoChange,
        onResultadoEvolucionChange = viewModel::onResultadoEvolucionChange,
        onDxCodigoChange = viewModel::onDxCodigoChange,
        onDxDescripcionChange = viewModel::onDxDescripcionChange,
        onRemoveDxRow = viewModel::removeDxRow,
        onAddDxRow = viewModel::addDxRow,
        onExamTipoChange = viewModel::onExamTipoChange,
        onExamNombreChange = viewModel::onExamNombreChange,
        onExamResultadoChange = viewModel::onExamResultadoChange,
        onExamUnidadChange = viewModel::onExamUnidadChange,
        onExamReferenciaChange = viewModel::onExamReferenciaChange,
        onExamFechaChange = viewModel::onExamFechaChange,
        onRemoveExamRow = viewModel::removeExamRow,
        onAddExamRow = viewModel::addExamRow,
        onExamenesObsChange = viewModel::onExamenesObsChange,
    )
}

/** All rendering lives here, taking [uiState] as plain data, so `*Previews.kt` never needs a real ViewModel. */
@Composable
@Suppress("LongParameterList")
internal fun NuevaEvolucionScreenContent(
    uiState: NuevaEvolucionUiState,
    patientId: String,
    hospId: String,
    sessionDisplayName: String,
    showDiscardDialog: Boolean,
    onClose: () -> Unit,
    onRequestDiscard: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onSave: () -> Unit,
    onSelectTab: (EvolucionTab) -> Unit,
    onSubjectiveChange: (String) -> Unit,
    onObjectiveChange: (String) -> Unit,
    onAssessmentChange: (String) -> Unit,
    onPlanChange: (String) -> Unit,
    onRxChange: (String) -> Unit,
    onPaChange: (String) -> Unit,
    onFcChange: (String) -> Unit,
    onFrChange: (String) -> Unit,
    onTempChange: (String) -> Unit,
    onSatO2Change: (String) -> Unit,
    onFio2Change: (String) -> Unit,
    onPronosticoChange: (String) -> Unit,
    onResultadoEvolucionChange: (String) -> Unit,
    onDxCodigoChange: (Int, String) -> Unit,
    onDxDescripcionChange: (Int, String) -> Unit,
    onRemoveDxRow: (Int) -> Unit,
    onAddDxRow: () -> Unit,
    onExamTipoChange: (Int, String) -> Unit,
    onExamNombreChange: (Int, String) -> Unit,
    onExamResultadoChange: (Int, String) -> Unit,
    onExamUnidadChange: (Int, String) -> Unit,
    onExamReferenciaChange: (Int, String) -> Unit,
    onExamFechaChange: (Int, String) -> Unit,
    onRemoveExamRow: (Int) -> Unit,
    onAddExamRow: () -> Unit,
    onExamenesObsChange: (String) -> Unit,
) {
    val patient = uiState.patient
    val hospitalizacion = uiState.hospitalizacion

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Text(
                "Cargando...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
            patient == null -> Text(
                "Paciente no encontrado: $patientId",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
            hospitalizacion == null -> Text(
                "Hospitalizacion no encontrada: $hospId",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
            else -> {
                EncounterTopBar(
                    title = "Nueva evolucion · ${patient.fullName} ${patient.medicalRecordNumber}",
                    onClose = onClose,
                    meta = {
                        Chip("${uiState.openedFecha} · ${uiState.openedHora}")
                        Chip(hospitalizacion.servicio)
                        Chip(sessionDisplayName)
                        StatusBadge(text = "En progreso", tone = BadgeTone.Progress)
                    },
                    actions = {
                        OutlinedButton(
                            onClick = onRequestDiscard,
                            enabled = !uiState.isSaving,
                            shape = fieldShape,
                            border = BorderStroke(1.5.dp, HissWarn),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("evo_new_discard_button"),
                        ) {
                            Text("Descartar", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissWarn)
                        }
                        Button(
                            onClick = onSave,
                            enabled = !uiState.isSaving,
                            shape = fieldShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("evo_new_save_button"),
                        ) {
                            Text("Guardar evolucion", fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
                        }
                    },
                )
                EvoTabBar(
                    selectedTab = uiState.selectedTab,
                    examCount = uiState.examenes.count { it.nombre.isNotBlank() },
                    onSelectTab = onSelectTab,
                )
                uiState.error?.let { error ->
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("screen_scroll_container")) {
                    when (uiState.selectedTab) {
                        EvolucionTab.EVOLUCION -> EvolucionPanel(
                            uiState = uiState,
                            onSubjectiveChange = onSubjectiveChange,
                            onObjectiveChange = onObjectiveChange,
                            onAssessmentChange = onAssessmentChange,
                            onPlanChange = onPlanChange,
                            onRxChange = onRxChange,
                            onPaChange = onPaChange,
                            onFcChange = onFcChange,
                            onFrChange = onFrChange,
                            onTempChange = onTempChange,
                            onSatO2Change = onSatO2Change,
                            onFio2Change = onFio2Change,
                            onDxCodigoChange = onDxCodigoChange,
                            onDxDescripcionChange = onDxDescripcionChange,
                            onRemoveDxRow = onRemoveDxRow,
                            onAddDxRow = onAddDxRow,
                            onPronosticoChange = onPronosticoChange,
                            onResultadoEvolucionChange = onResultadoEvolucionChange,
                        )
                        EvolucionTab.EXAMENES -> ExamenesPanel(
                            uiState = uiState,
                            onExamTipoChange = onExamTipoChange,
                            onExamNombreChange = onExamNombreChange,
                            onExamResultadoChange = onExamResultadoChange,
                            onExamUnidadChange = onExamUnidadChange,
                            onExamReferenciaChange = onExamReferenciaChange,
                            onExamFechaChange = onExamFechaChange,
                            onRemoveExamRow = onRemoveExamRow,
                            onAddExamRow = onAddExamRow,
                            onExamenesObsChange = onExamenesObsChange,
                        )
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = onDismissDiscardDialog,
            title = { Text("Descartar evolucion") },
            text = { Text("¿Descartar esta evolucion? Se perderan todos los datos ingresados.") },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDiscard,
                    modifier = Modifier.testTag("evo_new_discard_confirm_button"),
                ) {
                    Text("Descartar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDiscardDialog,
                    modifier = Modifier.testTag("evo_new_discard_cancel_button"),
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun EvoTabBar(
    selectedTab: EvolucionTab,
    examCount: Int,
    onSelectTab: (EvolucionTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EvoTab(
            "Evolucion",
            active = selectedTab == EvolucionTab.EVOLUCION,
            testTag = "evo_new_tab_evolucion",
        ) { onSelectTab(EvolucionTab.EVOLUCION) }
        EvoTab(
            "Examenes",
            active = selectedTab == EvolucionTab.EXAMENES,
            badgeCount = examCount.takeIf { it > 0 },
            testTag = "evo_new_tab_examenes",
        ) { onSelectTab(EvolucionTab.EXAMENES) }
    }
}

@Composable
private fun EvoTab(
    label: String,
    active: Boolean,
    badgeCount: Int? = null,
    testTag: String,
    onClick: () -> Unit,
) {
    val containerColor = if (active) HissAccentWash else Color.Transparent
    val borderColor = if (active) HissAccent else Color.Transparent
    val textColor = if (active) HissAccent else HissInk2
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(HissRadiusDefault),
        color = containerColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
            )
            if (badgeCount != null) {
                Text(
                    badgeCount.toString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier
                        .background(HissAccent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun EvolucionPanel(
    uiState: NuevaEvolucionUiState,
    onSubjectiveChange: (String) -> Unit,
    onObjectiveChange: (String) -> Unit,
    onAssessmentChange: (String) -> Unit,
    onPlanChange: (String) -> Unit,
    onRxChange: (String) -> Unit,
    onPaChange: (String) -> Unit,
    onFcChange: (String) -> Unit,
    onFrChange: (String) -> Unit,
    onTempChange: (String) -> Unit,
    onSatO2Change: (String) -> Unit,
    onFio2Change: (String) -> Unit,
    onDxCodigoChange: (Int, String) -> Unit,
    onDxDescripcionChange: (Int, String) -> Unit,
    onRemoveDxRow: (Int) -> Unit,
    onAddDxRow: () -> Unit,
    onPronosticoChange: (String) -> Unit,
    onResultadoEvolucionChange: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FormSectionCaption("Notas SOAP")
            SoapField(
                "Subjetivo",
                required = true,
                value = uiState.subjective,
                onValueChange = onSubjectiveChange,
                minLines = 4,
                testTag = "evo_new_subjective_field",
            )
            SoapField(
                "Objetivo",
                required = true,
                value = uiState.objective,
                onValueChange = onObjectiveChange,
                minLines = 4,
                testTag = "evo_new_objective_field",
            )
            SoapField(
                "Analisis",
                required = false,
                value = uiState.assessment,
                onValueChange = onAssessmentChange,
                minLines = 3,
                testTag = "evo_new_assessment_field",
            )
            SoapField(
                "Plan",
                required = false,
                value = uiState.plan,
                onValueChange = onPlanChange,
                minLines = 3,
                testTag = "evo_new_plan_field",
            )
            SoapField(
                "Prescripcion",
                required = false,
                value = uiState.rx,
                onValueChange = onRxChange,
                minLines = 4,
                testTag = "evo_new_rx_field",
            )
            Text(
                "Ej: Amoxicilina 500mg — TID — 7 dias",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = HissInk2,
            )
        }
        Column(
            modifier = Modifier.weight(1f).dashedStartBorder(HissFaint).padding(start = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FormSectionCaption("Resultado")
            FormSectionCaption("Signos Vitales")
            VitalsGrid(
                uiState = uiState,
                onPaChange = onPaChange,
                onFcChange = onFcChange,
                onFrChange = onFrChange,
                onTempChange = onTempChange,
                onSatO2Change = onSatO2Change,
                onFio2Change = onFio2Change,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RequiredFieldLabel("Diagnosticos")
                DiagnosisRows(
                    rows = uiState.diagnosticos,
                    onCodigoChange = onDxCodigoChange,
                    onDescripcionChange = onDxDescripcionChange,
                    onRemoveRow = onRemoveDxRow,
                    onAddRow = onAddDxRow,
                )
            }
            SelectField(
                label = { RequiredFieldLabel("Pronostico") },
                options = Pronostico.entries.map { it.toDisplayLabel() },
                selected = uiState.pronostico,
                onSelect = onPronosticoChange,
                testTag = "evo_new_pronostico_field",
            )
            SelectField(
                label = { RequiredFieldLabel("Evolucion") },
                options = EvolucionResultado.entries.map { it.toDisplayLabel() },
                selected = uiState.resultadoEvolucion,
                onSelect = onResultadoEvolucionChange,
                testTag = "evo_new_resultado_field",
            )
        }
    }
}

@Composable
private fun SoapField(
    label: String,
    required: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int,
    testTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { if (required) RequiredFieldLabel(label) else FieldLabel(label) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
        shape = fieldShape,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

/** Mirrors `.field label`'s resolved style (`font-size:12px; color: var(--ink2); font-weight:500`). */
@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, color = HissInk2, fontWeight = FontWeight.Medium)
}

@Composable
@Suppress("LongParameterList")
private fun VitalsGrid(
    uiState: NuevaEvolucionUiState,
    onPaChange: (String) -> Unit,
    onFcChange: (String) -> Unit,
    onFrChange: (String) -> Unit,
    onTempChange: (String) -> Unit,
    onSatO2Change: (String) -> Unit,
    onFio2Change: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalField("PA (mmHg)", uiState.pa, onPaChange, Modifier.weight(1f).testTag("evo_new_vital_pa_field"))
            VitalField("FC (lpm)", uiState.fc, onFcChange, Modifier.weight(1f).testTag("evo_new_vital_fc_field"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalField("FR (rpm)", uiState.fr, onFrChange, Modifier.weight(1f).testTag("evo_new_vital_fr_field"))
            VitalField("T° (°C)", uiState.temp, onTempChange, Modifier.weight(1f).testTag("evo_new_vital_temp_field"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalField("SatO2 (%)", uiState.satO2, onSatO2Change, Modifier.weight(1f).testTag("evo_new_vital_sato2_field"))
            VitalField("FiO2 (%)", uiState.fio2, onFio2Change, Modifier.weight(1f).testTag("evo_new_vital_fio2_field"))
        }
    }
}

@Composable
private fun VitalField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { FieldLabel(label) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
        shape = fieldShape,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun DiagnosisRows(
    rows: List<DxRow>,
    onCodigoChange: (Int, String) -> Unit,
    onDescripcionChange: (Int, String) -> Unit,
    onRemoveRow: (Int) -> Unit,
    onAddRow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.codigoCie10,
                    onValueChange = { onCodigoChange(index, it) },
                    placeholder = { Text("CIE-10", fontSize = FieldFontSize) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    singleLine = true,
                    modifier = Modifier.width(96.dp).testTag("evo_new_dx_codigo_$index"),
                )
                OutlinedTextField(
                    value = row.descripcion,
                    onValueChange = { onDescripcionChange(index, it) },
                    placeholder = { Text("Descripcion del diagnostico", fontSize = FieldFontSize) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("evo_new_dx_descripcion_$index"),
                )
                RemoveRowButton(onClick = { onRemoveRow(index) }, testTag = "evo_new_dx_remove_$index")
            }
        }
        GhostSmallButton("+ Agregar diagnostico", onClick = onAddRow, testTag = "evo_new_dx_add_button")
    }
}

@Composable
@Suppress("LongParameterList")
private fun ExamenesPanel(
    uiState: NuevaEvolucionUiState,
    onExamTipoChange: (Int, String) -> Unit,
    onExamNombreChange: (Int, String) -> Unit,
    onExamResultadoChange: (Int, String) -> Unit,
    onExamUnidadChange: (Int, String) -> Unit,
    onExamReferenciaChange: (Int, String) -> Unit,
    onExamFechaChange: (Int, String) -> Unit,
    onRemoveExamRow: (Int) -> Unit,
    onAddExamRow: () -> Unit,
    onExamenesObsChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        FormSectionCaption("Examenes y resultados")
        ExamRows(
            rows = uiState.examenes,
            onTipoChange = onExamTipoChange,
            onNombreChange = onExamNombreChange,
            onResultadoChange = onExamResultadoChange,
            onUnidadChange = onExamUnidadChange,
            onReferenciaChange = onExamReferenciaChange,
            onFechaChange = onExamFechaChange,
            onRemoveRow = onRemoveExamRow,
            onAddRow = onAddExamRow,
        )
        OutlinedTextField(
            value = uiState.examenesObs,
            onValueChange = onExamenesObsChange,
            label = { FieldLabel("Observaciones / informe") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
            shape = fieldShape,
            minLines = 8,
            modifier = Modifier.fillMaxWidth().testTag("evo_new_examenes_obs_field"),
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun ExamRows(
    rows: List<ExamRow>,
    onTipoChange: (Int, String) -> Unit,
    onNombreChange: (Int, String) -> Unit,
    onResultadoChange: (Int, String) -> Unit,
    onUnidadChange: (Int, String) -> Unit,
    onReferenciaChange: (Int, String) -> Unit,
    onFechaChange: (Int, String) -> Unit,
    onRemoveRow: (Int) -> Unit,
    onAddRow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tipo", "Examen", "Resultado", "Unidad", "Valor referencial", "Fecha").forEach { label ->
                Text(
                    label.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                    color = HissInk2,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.width(36.dp))
        }
        rows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SelectField(
                    label = {},
                    options = examTipoOptions,
                    selected = row.tipo,
                    onSelect = { onTipoChange(index, it) },
                    modifier = Modifier.weight(1f),
                    testTag = "evo_new_exam_tipo_$index",
                )
                ExamTextField(
                    row.nombre,
                    "Ej: Hemoglobina",
                    Modifier.weight(1f).testTag("evo_new_exam_nombre_$index"),
                ) { onNombreChange(index, it) }
                ExamTextField(
                    row.resultado,
                    "Ej: 11.2",
                    Modifier.weight(1f).testTag("evo_new_exam_resultado_$index"),
                ) { onResultadoChange(index, it) }
                ExamTextField(
                    row.unidad,
                    "g/dL",
                    Modifier.weight(1f).testTag("evo_new_exam_unidad_$index"),
                ) { onUnidadChange(index, it) }
                ExamTextField(
                    row.referencia,
                    "12.0 - 15.5",
                    Modifier.weight(1f).testTag("evo_new_exam_referencia_$index"),
                ) { onReferenciaChange(index, it) }
                ExamTextField(
                    row.fecha,
                    "DD/MM/AAAA",
                    Modifier.weight(1f).testTag("evo_new_exam_fecha_$index"),
                ) { onFechaChange(index, it) }
                RemoveRowButton(
                    onClick = { onRemoveRow(index) },
                    modifier = Modifier.width(36.dp),
                    testTag = "evo_new_exam_remove_$index",
                )
            }
        }
        GhostSmallButton("+ Agregar examen", onClick = onAddRow, testTag = "evo_new_exam_add_button")
    }
}

@Composable
private fun ExamTextField(value: String, placeholder: String, modifier: Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = FieldFontSize) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
        shape = fieldShape,
        singleLine = true,
        modifier = modifier,
    )
}

/** Mirrors `.btn.btn-ghost.btn-sm` (`border-color: var(--ink)`, dashed -- see the app-wide solid-vs-dashed note this ticket's fidelity pass left unfixed). */
@Composable
private fun GhostSmallButton(text: String, onClick: () -> Unit, testTag: String) {
    OutlinedButton(
        onClick = onClick,
        shape = fieldShape,
        border = BorderStroke(1.5.dp, HissInk),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier.testTag(testTag),
    ) {
        Text(text, fontSize = 12.sp, color = HissInk)
    }
}

@Composable
private fun RemoveRowButton(onClick: () -> Unit, modifier: Modifier = Modifier, testTag: String) {
    OutlinedButton(
        onClick = onClick,
        shape = fieldShape,
        border = BorderStroke(1.5.dp, HissInk),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = modifier.testTag(testTag),
    ) {
        Text("✕", fontSize = 12.sp, color = HissInk)
    }
}

private fun Modifier.dashedStartBorder(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(0f, size.height),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}
