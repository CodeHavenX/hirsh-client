package com.cramsan.hirsh.ui.screens.hospitalization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.completionCount
import com.cramsan.hirsh.model.statusLabel
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.BadgeTone
import com.cramsan.hirsh.ui.components.KeyValueRow
import com.cramsan.hirsh.ui.components.StatusBadge
import com.cramsan.hirsh.ui.components.VisitCard
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import org.koin.compose.viewmodel.koinViewModel

private val ButtonShape = RoundedCornerShape(HissRadiusDefault)

@Composable
fun HospitalizationScreen(
    patientId: String,
    hospId: String,
    onNewEvolucion: () -> Unit,
    onOpenHistoriaClinica: () -> Unit,
    onEvolucionSelected: (evoId: String) -> Unit,
    viewModel: HospitalizationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDischargeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(patientId, hospId) {
        viewModel.load(patientId, hospId)
    }

    val patient = uiState.patient
    val hospitalizacion = uiState.hospitalizacion

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            patient == null -> Text("Paciente no encontrado: $patientId", style = MaterialTheme.typography.bodyMedium)
            hospitalizacion == null ->
                Text("Hospitalizacion no encontrada: $hospId", style = MaterialTheme.typography.bodyMedium)
            else -> {
                HospitalizationHeader(
                    title = "${hospitalizacion.servicio} · ${patient.name}",
                    canDischarge = hospitalizacion.estado == EstadoHospitalizacion.ACTIVA,
                    isDischarging = uiState.isDischarging,
                    onNewEvolucion = onNewEvolucion,
                    onRequestDischarge = { showDischargeDialog = true },
                )
                InfoCard(patient, hospitalizacion, modifier = Modifier.padding(top = 20.dp))
                HistoriaClinicaStrip(
                    historiaClinica = hospitalizacion.historiaClinica,
                    onOpenHistoriaClinica = onOpenHistoriaClinica,
                    modifier = Modifier.padding(top = 16.dp),
                )
                EvolucionesSection(
                    evoluciones = hospitalizacion.evoluciones,
                    onEvolucionSelected = onEvolucionSelected,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }

    if (showDischargeDialog) {
        AlertDialog(
            onDismissRequest = { showDischargeDialog = false },
            title = { Text("Dar de alta") },
            text = { Text("¿Dar de alta a este paciente? La hospitalizacion pasara a estado Alta.") },
            confirmButton = {
                TextButton(onClick = {
                    showDischargeDialog = false
                    viewModel.discharge()
                }) {
                    Text("Dar de alta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDischargeDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun HospitalizationHeader(
    title: String,
    canDischarge: Boolean,
    isDischarging: Boolean,
    onNewEvolucion: () -> Unit,
    onRequestDischarge: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canDischarge) {
                OutlinedButton(
                    onClick = onRequestDischarge,
                    enabled = !isDischarging,
                    shape = ButtonShape,
                    border = BorderStroke(1.5.dp, HissInk),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Dar de alta", fontWeight = FontWeight.Medium, color = HissInk)
                }
            }
            Button(
                onClick = onNewEvolucion,
                shape = ButtonShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("+ Nueva evolucion", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun InfoCard(patient: Patient, hospitalizacion: Hospitalizacion, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(HissRadiusDefault),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HissFaint),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = HissAccentWash,
                    border = BorderStroke(1.5.dp, HissAccent),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = initialsOf(patient.name),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = HissAccent,
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(patient.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(patient.id, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HissInk2)
                }
            }
            HorizontalDivider(color = HissFaint)
            KeyValueRow("Servicio", hospitalizacion.servicio)
            KeyValueRow("Cama", hospitalizacion.cama)
            KeyValueRow("Medico responsable", hospitalizacion.medicoResponsable)
            KeyValueRow("Fecha ingreso", "${hospitalizacion.fechaIngreso} · ${hospitalizacion.horaIngreso}")
            KeyValueRow(
                "Fecha alta",
                if (hospitalizacion.fechaAlta != null) "${hospitalizacion.fechaAlta} · ${hospitalizacion.horaAlta}" else "En curso",
            )
            KeyValueRow("Estado") { EstadoBadge(hospitalizacion.estado) }
            Column(modifier = Modifier.fillMaxWidth().dashedTopBorder(HissFaint).padding(top = 8.dp)) {
                Text(
                    "Motivo de ingreso: ${hospitalizacion.motivoIngreso}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HissInk2,
                )
            }
        }
    }
}

@Composable
private fun HistoriaClinicaStrip(
    historiaClinica: HistoriaClinica,
    onOpenHistoriaClinica: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(HissRadiusDefault),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HissFaint),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "HISTORIA CLINICA",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.Medium,
                    color = HissAccent,
                )
                HcStatusBadge(historiaClinica)
            }
            Button(
                onClick = onOpenHistoriaClinica,
                shape = ButtonShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("Abrir Historia Clinica", fontWeight = FontWeight.Medium)
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

@Composable
private fun EvolucionesSection(
    evoluciones: List<Evolucion>,
    onEvolucionSelected: (evoId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Evoluciones", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "${evoluciones.size} evoluciones",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = HissInk2,
            )
        }
        if (evoluciones.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(HissRadiusDefault),
                color = Color.Transparent,
                border = BorderStroke(1.dp, HissFaint),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(
                    "Sin evoluciones registradas. Inicie una nueva evolucion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HissInk2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            }
        } else {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                evoluciones.forEachIndexed { index, evolucion ->
                    VisitCard(
                        title = evolucion.diagnosticos.joinToString(", ") { it.descripcion }.ifEmpty { "Evolucion" },
                        meta = "${evolucion.fecha} · ${evolucion.hora} · ${evolucion.medico}",
                        summary = evolucion.subjective.take(90),
                        active = index == 0,
                        onClick = { onEvolucionSelected(evolucion.id) },
                        trailing = { StatusBadge(text = evolucion.resultado.toDisplayLabel(), tone = BadgeTone.Done) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EstadoBadge(estado: EstadoHospitalizacion) {
    val (label, tone) = when (estado) {
        EstadoHospitalizacion.ACTIVA -> "Activa" to BadgeTone.Progress
        EstadoHospitalizacion.ALTA -> "Alta" to BadgeTone.Done
    }
    StatusBadge(text = label, tone = tone)
}

private fun initialsOf(name: String): String = name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString("")

/** Mirrors the dashed-stroke technique in [StatusBadge] -- `.audit`'s `border-top: 1px dashed var(--faint)`. */
private fun Modifier.dashedTopBorder(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}
