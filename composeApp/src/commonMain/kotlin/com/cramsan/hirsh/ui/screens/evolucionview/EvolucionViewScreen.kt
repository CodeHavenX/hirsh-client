package com.cramsan.hirsh.ui.screens.evolucionview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.BadgeTone
import com.cramsan.hirsh.ui.components.Chip
import com.cramsan.hirsh.ui.components.DataTable
import com.cramsan.hirsh.ui.components.DataTableColumn
import com.cramsan.hirsh.ui.components.EncounterTopBar
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.components.KeyValueRow
import com.cramsan.hirsh.ui.components.ReadOnlyField
import com.cramsan.hirsh.ui.components.StatusBadge
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EvolucionViewScreen(
    patientId: String,
    hospId: String,
    evoId: String,
    onClose: () -> Unit,
    viewModel: EvolucionViewViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(patientId, hospId, evoId) {
        viewModel.load(patientId, hospId, evoId)
    }

    val patient = uiState.patient
    val hospitalizacion = uiState.hospitalizacion
    val evolucion = uiState.evolucion

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
            evolucion == null -> Text(
                "Evolucion no encontrada: $evoId",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
            else -> {
                EncounterTopBar(
                    title = "Evolucion · ${patient.name} ${patient.id}",
                    onClose = onClose,
                    meta = {
                        Chip("${evolucion.fecha} · ${evolucion.hora}")
                        Chip(evolucion.medico)
                        StatusBadge(text = "Registrada · solo lectura", tone = BadgeTone.Done)
                    },
                    actions = {
                        Button(
                            onClick = viewModel::openPrintPreview,
                            shape = fieldShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text("Imprimir", fontWeight = FontWeight.Medium)
                        }
                    },
                )
                if (uiState.showPrintPreview) {
                    Dialog(
                        onDismissRequest = viewModel::closePrintPreview,
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        EvolucionPrintable(
                            patient = patient,
                            hospitalizacion = hospitalizacion,
                            evolucion = evolucion,
                            onBack = viewModel::closePrintPreview,
                        )
                    }
                }
                EvoTabBar(selectedTab = uiState.selectedTab, examCount = evolucion.examenes.size, onSelectTab = viewModel::selectTab)
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    when (uiState.selectedTab) {
                        EvolucionViewTab.EVOLUCION -> EvolucionReadOnlyPanel(evolucion)
                        EvolucionViewTab.EXAMENES -> ExamenesReadOnlyPanel(evolucion)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvoTabBar(
    selectedTab: EvolucionViewTab,
    examCount: Int,
    onSelectTab: (EvolucionViewTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EvoTab("Evolucion", active = selectedTab == EvolucionViewTab.EVOLUCION) { onSelectTab(EvolucionViewTab.EVOLUCION) }
        EvoTab(
            "Examenes",
            active = selectedTab == EvolucionViewTab.EXAMENES,
            badgeCount = examCount.takeIf { it > 0 },
        ) { onSelectTab(EvolucionViewTab.EXAMENES) }
    }
}

@Composable
private fun EvoTab(
    label: String,
    active: Boolean,
    badgeCount: Int? = null,
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
private fun EvolucionReadOnlyPanel(evolucion: Evolucion) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FormSectionCaption("Notas SOAP")
            ReadOnlyField("Subjetivo", evolucion.subjective)
            ReadOnlyField("Objetivo", evolucion.objective)
            ReadOnlyField("Analisis", evolucion.assessment)
            ReadOnlyField("Plan", evolucion.plan)
        }
        Column(
            modifier = Modifier.weight(1f).dashedStartBorder(HissFaint).padding(start = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FormSectionCaption("Resultado")
            val dxList = evolucion.diagnosticos.joinToString("\n") { dx ->
                if (dx.codigoCie10.isNotBlank() && dx.codigoCie10 != "—") "${dx.descripcion} (${dx.codigoCie10})" else dx.descripcion
            }.ifBlank { "—" }
            ReadOnlyField("Diagnosticos", dxList)
            RxBox(evolucion.rx.ifBlank { "—" })
            Column {
                KeyValueRow("Pronostico", evolucion.pronostico.toDisplayLabel())
                KeyValueRow("Evolucion", evolucion.resultado.toDisplayLabel())
            }
            FormSectionCaption("Signos Vitales")
            VitalsKvGrid(evolucion)
        }
    }
}

@Composable
private fun RxBox(text: String) {
    Column {
        Text(
            "PRESCRIPCION",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = HissInk2,
        )
        Surface(
            shape = RoundedCornerShape(HissRadiusDefault),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, HissFaint),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun VitalsKvGrid(evolucion: Evolucion) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                KeyValueRow("PA", evolucion.vitals.pa.ifBlank { "—" })
                KeyValueRow("FR", evolucion.vitals.fr.ifBlank { "—" })
                KeyValueRow("SatO2", evolucion.vitals.satO2.ifBlank { "—" })
            }
            Column(modifier = Modifier.weight(1f)) {
                KeyValueRow("FC", evolucion.vitals.fc.ifBlank { "—" })
                KeyValueRow("T°", evolucion.vitals.temp.ifBlank { "—" })
                KeyValueRow("FiO2", evolucion.vitals.fio2.ifBlank { "—" })
            }
        }
    }
}

@Composable
private fun ExamenesReadOnlyPanel(evolucion: Evolucion) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        FormSectionCaption("Examenes y resultados")
        if (evolucion.examenes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(HissRadiusDefault),
                color = Color.Transparent,
                border = BorderStroke(1.dp, HissFaint),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Sin examenes registrados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HissInk2,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            }
        } else {
            DataTable(
                columns = listOf(
                    DataTableColumn(label = "Tipo", weight = 0.8f) { e -> Text(e.tipo.ifBlank { "—" }, fontSize = 13.sp) },
                    DataTableColumn(label = "Examen", weight = 2f) {
                        Text(it.nombre.ifBlank { "—" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    },
                    DataTableColumn(label = "Resultado", weight = 1f) { Text(it.resultado.ifBlank { "—" }, fontSize = 13.sp) },
                    DataTableColumn(label = "Unidad", weight = 0.6f) { Text(it.unidad.ifBlank { "—" }, fontSize = 13.sp) },
                    DataTableColumn(label = "Valor referencial", weight = 1.2f) { Text(it.referencia.ifBlank { "—" }, fontSize = 13.sp) },
                    DataTableColumn(label = "Fecha", weight = 0.8f) { Text(it.fecha.ifBlank { "—" }, fontSize = 13.sp) },
                ),
                rows = evolucion.examenes,
            )
        }
        ReadOnlyField("Observaciones / informe", evolucion.examenesObs.ifBlank { "—" })
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
