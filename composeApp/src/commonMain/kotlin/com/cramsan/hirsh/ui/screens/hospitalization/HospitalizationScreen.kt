package com.cramsan.hirsh.ui.screens.hospitalization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.cramsan.hirsh.model.ActiveTreatment
import com.cramsan.hirsh.model.AppliedScale
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.bedNumber
import com.cramsan.hirsh.model.completionCount
import com.cramsan.hirsh.model.physicianName
import com.cramsan.hirsh.model.serviceName
import com.cramsan.hirsh.model.statusLabel
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.model.totalLabel
import com.cramsan.hirsh.ui.components.ActiveTreatmentsPanel
import com.cramsan.hirsh.ui.components.BadgeTone
import com.cramsan.hirsh.ui.components.EpisodeStatusBadge
import com.cramsan.hirsh.ui.components.KeyValueRow
import com.cramsan.hirsh.ui.components.LoadErrorMessage
import com.cramsan.hirsh.ui.components.NoteStatusBadge
import com.cramsan.hirsh.ui.components.PdfDownload
import com.cramsan.hirsh.ui.components.PdfDownloadButton
import com.cramsan.hirsh.ui.components.PdfDownloadStatus
import com.cramsan.hirsh.ui.components.ScaleSeverityBadge
import com.cramsan.hirsh.ui.components.StatusBadge
import com.cramsan.hirsh.ui.components.SuicideAlertBadge
import com.cramsan.hirsh.ui.components.SuspendTreatmentDialog
import com.cramsan.hirsh.ui.components.VisitCard
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.ui.theme.HissWarn
import com.cramsan.hirsh.util.formatDateTime
import org.koin.compose.viewmodel.koinViewModel

private val ButtonShape = RoundedCornerShape(HissRadiusDefault)
private val NARROW_HEADER_WIDTH = 600.dp

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
        onDownloadPdf = viewModel::downloadPdf,
        onSuspendTreatment = viewModel::openSuspension,
        onSuspensionReasonChange = viewModel::onSuspensionReasonChange,
        onConfirmSuspension = viewModel::confirmSuspension,
        onDismissSuspension = viewModel::dismissSuspension,
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
    onSuspendTreatment: (ActiveTreatment) -> Unit = {},
    onSuspensionReasonChange: (String) -> Unit = {},
    onConfirmSuspension: () -> Unit = {},
    onDismissSuspension: () -> Unit = {},
    onDownloadPdf: () -> Unit = {},
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
                    pdfDownload = uiState.pdfDownload,
                    onDownloadPdf = onDownloadPdf.takeIf { uiState.canDownloadPdf },
                )
                PdfDownloadStatus(uiState.pdfDownload, modifier = Modifier.padding(top = 8.dp))
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
                    ActiveTreatmentsPanel(
                        section = uiState.treatments,
                        onSuspend = onSuspendTreatment.takeIf { uiState.canSuspendTreatments },
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    AppliedScalesList(
                        section = uiState.scales,
                        onScaleSelected = { onEvolucionSelected(it.documentId) },
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
                uiState.suspension?.let {
                    SuspendTreatmentDialog(
                        suspension = it,
                        onReasonChange = onSuspensionReasonChange,
                        onConfirm = onConfirmSuspension,
                        onDismiss = onDismissSuspension,
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
    pdfDownload: PdfDownload,
    onDownloadPdf: (() -> Unit)?,
) {
    val titleText: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
    val actions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDownloadPdf != null) {
                PdfDownloadButton(pdfDownload, onClick = onDownloadPdf)
            }
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
    // Three actions leave a phone no room for the title beside them, so it goes above.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < NARROW_HEADER_WIDTH) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                titleText(Modifier)
                actions()
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                titleText(Modifier.weight(1f, fill = false).padding(end = 12.dp))
                actions()
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

/** Each result opens the evolucion it was applied in. */
@Composable
internal fun AppliedScalesList(
    section: AppliedScalesSection,
    onScaleSelected: (AppliedScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (section is AppliedScalesSection.Hidden) return
    val scales = (section as? AppliedScalesSection.Loaded)?.scales.orEmpty()
    Column(modifier = modifier.testTag("hosp_scales_section")) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Escalas aplicadas", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${scales.size} escalas", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HissInk2)
        }
        when {
            section is AppliedScalesSection.Failed -> EmptyBox("No se pudieron cargar las escalas aplicadas.")
            scales.isEmpty() -> EmptyBox("Sin escalas aplicadas. Aplique una desde una evolucion.")
            else -> {
                ScaleSeries(scales, modifier = Modifier.padding(top = 12.dp))
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scales.forEach { scale -> AppliedScaleRow(scale, onClick = { onScaleSelected(scale) }) }
                }
            }
        }
    }
}

@Composable
private fun EmptyBox(text: String) {
    Surface(
        shape = RoundedCornerShape(HissRadiusDefault),
        color = Color.Transparent,
        border = BorderStroke(1.dp, HissFaint),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = HissInk2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
    }
}

/** How each scale applied more than once moved during the stay, oldest first. */
@Composable
private fun ScaleSeries(scales: List<AppliedScale>, modifier: Modifier = Modifier) {
    val series = scales.groupBy { it.scaleCode }.filterValues { it.size > 1 }
    if (series.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        series.forEach { (code, results) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("hosp_scale_series_$code"),
            ) {
                Text(code, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HissAccent, fontWeight = FontWeight.Medium)
                results.sortedBy { it.appliedAt }.forEachIndexed { index, result ->
                    if (index > 0) Text("→", fontSize = 12.sp, color = HissInk2)
                    Text(
                        result.score.totalLabel(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (result.score.raisesSuicideAlert) HissWarn else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppliedScaleRow(scale: AppliedScale, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(HissRadiusDefault),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (scale.score.raisesSuicideAlert) 1.5.dp else 1.dp, if (scale.score.raisesSuicideAlert) HissWarn else HissFaint),
        modifier = Modifier.fillMaxWidth().testTag("hosp_scale_${scale.id}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(scale.scaleName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDateTime(scale.appliedAt)} · ${scale.appliedBy}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = HissInk2,
                )
                scale.score.interpretation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HissInk2) }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(scale.score.totalLabel(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                scale.score.severity?.let { ScaleSeverityBadge(it) }
                if (scale.score.raisesSuicideAlert) SuicideAlertBadge(modifier = Modifier.testTag("hosp_scale_alert_${scale.id}"))
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
