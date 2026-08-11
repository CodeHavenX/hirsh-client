package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.EstadoHospitalizacion
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.PatientChangeLogEntry
import com.cramsan.hirsh.model.calculateAge
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.compose.viewmodel.koinViewModel

private val ButtonShape = RoundedCornerShape(HissRadiusDefault)

@OptIn(ExperimentalTime::class)
@Composable
fun PatientRecordScreen(
    patientId: String,
    onEditProfile: () -> Unit,
    onNewHospitalization: () -> Unit,
    onHospitalizationSelected: (hospId: String) -> Unit,
    onViewHistory: () -> Unit,
    viewModel: PatientRecordViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    LaunchedEffect(patientId) {
        viewModel.load(patientId)
    }

    val patient = uiState.patient
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            patient == null -> Text("Paciente no encontrado: $patientId", style = MaterialTheme.typography.bodyMedium)
            else -> {
                RecordHeader(
                    patient = patient,
                    onEditProfile = onEditProfile,
                    onNewHospitalization = onNewHospitalization,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    ProfileCard(
                        patient = patient,
                        today = today,
                        lastChangeLine = lastChangeLine(uiState.lastChange),
                        onViewHistory = onViewHistory,
                        modifier = Modifier.width(280.dp),
                    )
                    HospitalizationsSection(
                        hospitalizations = uiState.hospitalizations,
                        onHospitalizationSelected = onHospitalizationSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordHeader(
    patient: Patient,
    onEditProfile: () -> Unit,
    onNewHospitalization: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            patient.name,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onEditProfile,
                shape = ButtonShape,
                border = BorderStroke(1.5.dp, HissInk),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("record_edit_button"),
            ) {
                Text("Editar perfil", fontWeight = FontWeight.Medium, color = HissInk)
            }
            Button(
                onClick = onNewHospitalization,
                shape = ButtonShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("record_new_hospitalization_button"),
            ) {
                Text("+ Nueva hospitalizacion", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ProfileCard(
    patient: Patient,
    today: LocalDate,
    lastChangeLine: String,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(HissRadiusDefault),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HissFaint),
        modifier = modifier,
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
            KeyValueRow("DNI", patient.nationalId)
            KeyValueRow("Fecha nacimiento", patient.dateOfBirth)
            KeyValueRow("Edad", "${calculateAge(patient.dateOfBirth, today)} anos")
            KeyValueRow("Sexo", patient.sex.toDisplayLabel())
            KeyValueRow("Telefono", patient.phone)
            KeyValueRow("Grupo sanguineo", patient.bloodType)
            KeyValueRow("Alergias") {
                val tone = if (patient.allergies != "Ninguna") BadgeTone.Warn else BadgeTone.Off
                StatusBadge(text = patient.allergies, tone = tone)
            }
            KeyValueRow("Medico asignado", patient.assignedDoctor)
            Column(
                modifier = Modifier.fillMaxWidth().dashedTopBorder(HissFaint).padding(top = 8.dp),
            ) {
                Text(lastChangeLine, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = HissInk2)
                Text(
                    "Ver historial de cambios →",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HissAccent,
                    modifier = Modifier.padding(top = 2.dp).clickable(onClick = onViewHistory).testTag("record_history_link"),
                )
            }
        }
    }
}

private fun lastChangeLine(lastChange: PatientChangeLogEntry?): String =
    if (lastChange != null) {
        "Ultima edicion: ${lastChange.changedBy} · ${lastChange.fecha}, ${lastChange.hora}"
    } else {
        "Sin cambios registrados."
    }

@Composable
private fun HospitalizationsSection(
    hospitalizations: List<Hospitalizacion>,
    onHospitalizationSelected: (hospId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Hospitalizaciones", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "${hospitalizations.size} hospitalizaciones",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = HissInk2,
            )
        }
        if (hospitalizations.isEmpty()) {
            Text(
                "Sin hospitalizaciones registradas. Inicie una nueva hospitalizacion.",
                style = MaterialTheme.typography.bodyMedium,
                color = HissInk2,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hospitalizations.forEachIndexed { index, hospitalization ->
                    VisitCard(
                        title = hospitalization.servicio,
                        meta = "${hospitalization.fechaIngreso} → ${hospitalization.fechaAlta ?: "En curso"} · " +
                            hospitalization.medicoResponsable,
                        summary = hospitalization.motivoIngreso,
                        active = index == 0,
                        onClick = { onHospitalizationSelected(hospitalization.id) },
                        trailing = { EstadoBadge(hospitalization.estado) },
                        modifier = Modifier.testTag("hosp_card_${hospitalization.id}"),
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
