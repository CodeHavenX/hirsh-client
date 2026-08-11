package com.cramsan.hirsh.ui.screens.historiaclinica

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.completionCount
import com.cramsan.hirsh.model.statusLabel
import com.cramsan.hirsh.ui.components.BadgeTone
import com.cramsan.hirsh.ui.components.Chip
import com.cramsan.hirsh.ui.components.EncounterTopBar
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.components.SelectField
import com.cramsan.hirsh.ui.components.StatusBadge
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.ui.theme.HissSuccess
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HistoriaClinicaScreen(
    patientId: String,
    hospId: String,
    onClose: () -> Unit,
    viewModel: HistoriaClinicaViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(patientId, hospId) {
        viewModel.load(patientId, hospId)
    }

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
                val historiaClinica = hospitalizacion.historiaClinica
                EncounterTopBar(
                    title = "Historia Clinica · ${patient.name} ${patient.id}",
                    onClose = onClose,
                    meta = {
                        Chip(hospitalizacion.servicio)
                        Chip("Cama ${hospitalizacion.cama}")
                        HcStatusBadge(historiaClinica)
                    },
                    actions = {
                        Button(
                            onClick = {},
                            shape = fieldShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text("Imprimir", fontWeight = FontWeight.Medium)
                        }
                    },
                )
                Row(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    HcSectionNav(
                        historiaClinica = historiaClinica,
                        activeSection = uiState.activeSection,
                        onSelectSection = viewModel::selectSection,
                    )
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        FormSectionCaption(sectionLabel(uiState.activeSection))
                        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (uiState.activeSection == HcSectionKey.MOTIVO_INGRESO) {
                                MotivoIngresoForm(
                                    draft = uiState.motivoIngresoDraft,
                                    onOptionChange = viewModel::onMotivoOptionChange,
                                    onOtrosDetalleChange = viewModel::onMotivoOtrosDetalleChange,
                                )
                            } else {
                                SectionForm(
                                    specs = HC_SECTION_FIELDS[uiState.activeSection].orEmpty(),
                                    values = uiState.fieldValues,
                                    onFieldChange = viewModel::onFieldChange,
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = viewModel::save,
                                enabled = !uiState.isSaving,
                                shape = fieldShape,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text("Guardar seccion", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HcStatusBadge(historiaClinica: HistoriaClinica) {
    val (done, total) = historiaClinica.completionCount()
    val label = historiaClinica.statusLabel()
    val tone = when (label) {
        "Completa" -> BadgeTone.Done
        "Borrador" -> BadgeTone.Off
        else -> BadgeTone.Progress
    }
    StatusBadge(text = "$label · $done/$total secciones", tone = tone)
}

private fun sectionLabel(key: HcSectionKey): String = key.label

@Composable
private fun HcSectionNav(
    historiaClinica: HistoriaClinica,
    activeSection: HcSectionKey,
    onSelectSection: (HcSectionKey) -> Unit,
) {
    Column(
        modifier = Modifier.width(220.dp).dashedEndBorder(HissFaint).padding(end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HcSectionKey.entries.forEach { key ->
            if (!key.implemented) {
                HcNavStub(key.label)
            } else {
                HcNavItem(
                    label = key.label,
                    active = key == activeSection,
                    complete = historiaClinica.isSectionComplete(key),
                    onClick = { onSelectSection(key) },
                )
            }
        }
    }
}

@Composable
private fun HcNavItem(label: String, active: Boolean, complete: Boolean, onClick: () -> Unit) {
    val containerColor = if (active) HissAccentWash else Color.Transparent
    val borderColor = if (active) HissAccent else Color.Transparent
    val textColor = if (active) HissAccent else HissInk2
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(HissRadiusDefault),
        color = containerColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (complete) "✓" else "○",
                fontSize = 11.sp,
                // .hc-nav-item.active .hc-nav-ico (3 classes) outranks .hc-nav-ico-done (1 class) in the
                // mock's CSS cascade -- an active AND complete item shows the accent-colored icon, not
                // the success-green one; success only wins when complete but not active.
                color = if (!active && complete) HissSuccess else textColor,
                modifier = Modifier.width(16.dp),
            )
            Text(label, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = textColor)
        }
    }
}

@Composable
private fun HcNavStub(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(0.55f).padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("…", fontSize = 11.sp, color = HissInk2, modifier = Modifier.width(16.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HissInk2, modifier = Modifier.weight(1f))
        Text(
            "PROXIMAMENTE",
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            letterSpacing = 0.32.sp,
            color = HissInk2,
            modifier = Modifier
                .border(BorderStroke(1.dp, HissFaint), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun SectionForm(
    specs: List<HcFieldSpec>,
    values: Map<String, String>,
    onFieldChange: (String, String) -> Unit,
) {
    specs.forEach { spec ->
        val value = values[spec.key].orEmpty()
        when (spec.type) {
            HcFieldType.SELECT -> SelectField(
                label = { FieldLabel(spec.label) },
                options = spec.options,
                selected = value,
                onSelect = { onFieldChange(spec.key, it) },
            )
            HcFieldType.TEXTAREA -> OutlinedTextField(
                value = value,
                onValueChange = { onFieldChange(spec.key, it) },
                label = { FieldLabel(spec.label) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                shape = fieldShape,
                minLines = spec.rows,
                modifier = Modifier.fillMaxWidth(),
            )
            HcFieldType.TEXT -> OutlinedTextField(
                value = value,
                onValueChange = { onFieldChange(spec.key, it) },
                label = { FieldLabel(spec.label) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                shape = fieldShape,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Mirrors `.field label`'s resolved style (`font-size:12px; color: var(--ink2); font-weight:500`). */
@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, color = HissInk2, fontWeight = FontWeight.Medium)
}

@Composable
private fun MotivoIngresoForm(
    draft: MotivoIngreso,
    onOptionChange: (MotivoIngreso, String, Boolean) -> Unit,
    onOtrosDetalleChange: (MotivoIngreso, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Motivos de ingreso (marcar los que correspondan)")
            FlowOfMotivoOptions(draft = draft, onOptionChange = onOptionChange)
        }
        OutlinedTextField(
            value = draft.otrosDetalle,
            onValueChange = { onOtrosDetalleChange(draft, it) },
            label = { FieldLabel("Detalle si \"Otros\"") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
            shape = fieldShape,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FlowOfMotivoOptions(
    draft: MotivoIngreso,
    onOptionChange: (MotivoIngreso, String, Boolean) -> Unit,
) {
    val rows = MOTIVO_INGRESO_OPTIONS.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    MotivoOptionChip(
                        option = option,
                        checked = draft.isChecked(option.key),
                        onCheckedChange = { checked -> onOptionChange(draft, option.key, checked) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowOptions.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MotivoOptionChip(
    option: MotivoIngresoOption,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.5.dp, HissFaint),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(option.label, fontSize = 13.sp, color = HissInk)
        }
    }
}

private fun Modifier.dashedEndBorder(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(size.width, 0f),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}
