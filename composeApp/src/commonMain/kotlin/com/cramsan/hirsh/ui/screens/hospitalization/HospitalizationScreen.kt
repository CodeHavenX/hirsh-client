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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.bedNumber
import com.cramsan.hirsh.model.completionCount
import com.cramsan.hirsh.model.physicianName
import com.cramsan.hirsh.model.serviceName
import com.cramsan.hirsh.model.statusLabel
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.BadgeTone
import com.cramsan.hirsh.ui.components.EpisodeStatusBadge
import com.cramsan.hirsh.ui.components.KeyValueRow
import com.cramsan.hirsh.ui.components.LoadErrorMessage
import com.cramsan.hirsh.ui.components.NoteStatusBadge
import com.cramsan.hirsh.ui.components.StatusBadge
import com.cramsan.hirsh.ui.components.VisitCard
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.util.formatDateTime
import org.koin.compose.viewmodel.koinViewModel

private val ButtonShape = RoundedCornerShape(HissRadiusDefault)

@Composable
fun HospitalizationScreen(
    patientId: String,
    hospId: String,
    onNewEvolucion: () -> Unit,
    onOpenHistoriaClinica: () -> Unit,
    onEvolucionSelected: (evoId: String) -> Unit,
    onDischarge: () -> Unit,
    viewModel: HospitalizationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(patientId, hospId) {
        viewModel.load(patientId, hospId)
    }

    HospitalizationScreenContent(
        uiState = uiState,
        onNewEvolucion = onNewEvolucion,
        onOpenHistoriaClinica = onOpenHistoriaClinica,
        onEvolucionSelected = onEvolucionSelected,
        onDischarge = onDischarge,
        onRetry = viewModel::retry,
    )
}

/** All rendering lives here, taking [uiState] as plain data, so `*Previews.kt` never needs a real ViewModel. */
@Composable
internal fun HospitalizationScreenContent(
    uiState: HospitalizationUiState,
    onNewEvolucion: () -> Unit,
    onOpenHistoriaClinica: () -> Unit,
    onEvolucionSelected: (evoId: String) -> Unit,
    onDischarge: () -> Unit,
    onRetry: () -> Unit,
) {
    val patient = uiState.patient
    val hospitalizacion = uiState.hospitalizacion

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            uiState.loadError != null -> LoadErrorMessage(uiState.loadError, onRetry = onRetry)
            patient == null -> Text("Paciente no encontrado", style = MaterialTheme.typography.bodyMedium)
            hospitalizacion == null ->
                Text("Hospitalizacion no encontrada", style = MaterialTheme.typography.bodyMedium)
            else -> {
                HospitalizationHeader(
                    title = "${hospitalizacion.serviceName} · ${patient.fullName}",
                    canDischarge = hospitalizacion.status == EpisodeStatus.ACTIVE,
                    onNewEvolucion = onNewEvolucion,
                    onDischarge = onDischarge,
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .testTag("screen_scroll_container"),
                ) {
                    InfoCard(patient, hospitalizacion, modifier = Modifier.padding(top = 20.dp))
                    HistoriaClinicaStrip(
                        historiaClinica = uiState.historiaClinica,
                        onOpenHistoriaClinica = onOpenHistoriaClinica,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    EvolucionesSection(
                        evoluciones = uiState.evoluciones,
                        onEvolucionSelected = onEvolucionSelected,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HospitalizationHeader(
    title: String,
    canDischarge: Boolean,
    onNewEvolucion: () -> Unit,
    onDischarge: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canDischarge) {
                OutlinedButton(
                    onClick = onDischarge,
                    shape = ButtonShape,
                    border = BorderStroke(1.5.dp, HissInk),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("hosp_discharge_button"),
                ) {
                    Text("Dar de alta", fontWeight = FontWeight.Medium, color = HissInk)
                }
            }
            Button(
                onClick = onNewEvolucion,
                shape = ButtonShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("hosp_new_evolucion_button"),
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
                            text = initialsOf(patient.fullName),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = HissAccent,
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(patient.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(patient.medicalRecordNumber, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HissInk2)
                }
            }
            HorizontalDivider(color = HissFaint)
            KeyValueRow("Servicio", hospitalizacion.serviceName)
            KeyValueRow("Cama", hospitalizacion.bedNumber)
            KeyValueRow("Medico responsable", hospitalizacion.physicianName)
            KeyValueRow("Fecha ingreso", formatDateTime(hospitalizacion.startDate))
            KeyValueRow("Fecha alta", hospitalizacion.endDate?.let { formatDateTime(it) } ?: "En curso")
            KeyValueRow("Estado") { EpisodeStatusBadge(hospitalizacion.status) }
            hospitalizacion.discharge?.let { discharge ->
                KeyValueRow("Tipo de alta", discharge.type.toDisplayLabel())
                KeyValueRow("Condicion al alta", discharge.condition.toDisplayLabel())
                KeyValueRow("Diagnostico de alta (CIE-10)", discharge.diagnosisIcd10)
                KeyValueRow("Proxima cita", discharge.nextAppointmentDate ?: "—")
                KeyValueRow("Plan de seguimiento", discharge.followUpPlan ?: "—")
            }
            Column(modifier = Modifier.fillMaxWidth().dashedTopBorder(HissFaint).padding(top = 8.dp)) {
                Text(
                    "Motivo de ingreso: ${hospitalizacion.admissionReason ?: "—"}",
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HcStatusBadge(historiaClinica)
                    historiaClinica.status?.let { NoteStatusBadge(it, historiaClinica.draftDeadline) }
                }
            }
            Button(
                onClick = onOpenHistoriaClinica,
                shape = ButtonShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("hosp_open_hc_button"),
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
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (evolucion.status != NoteStatus.SIGNED) NoteStatusBadge(evolucion.status)
                                StatusBadge(text = evolucion.resultado.toDisplayLabel(), tone = BadgeTone.Done)
                            }
                        },
                        modifier = Modifier.testTag("evo_card_${evolucion.id}"),
                    )
                }
            }
        }
    }
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
