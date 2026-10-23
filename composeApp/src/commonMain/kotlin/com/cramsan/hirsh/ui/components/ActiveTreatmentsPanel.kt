package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.ActiveTreatment
import com.cramsan.hirsh.model.toShortLabel
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.ui.theme.HissWarn
import com.cramsan.hirsh.util.formatDate

/**
 * What the patient is being given, from signed evoluciones' prescriptions (HCEM-11). [onSuspend]
 * is null for users without TREATMENT_SUSPEND, which hides the button.
 */
@Composable
fun ActiveTreatmentsPanel(
    section: ActiveTreatmentsSection,
    onSuspend: ((ActiveTreatment) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val treatments = (section as? ActiveTreatmentsSection.Loaded)?.treatments.orEmpty()
    Column(modifier = modifier.testTag("active_treatments_section")) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Tratamiento activo", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (section is ActiveTreatmentsSection.Loaded) {
                Text("${treatments.size} medicamentos", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HissInk2)
            }
        }
        when (section) {
            ActiveTreatmentsSection.Loading -> PanelMessage("Cargando...")
            ActiveTreatmentsSection.Failed -> PanelMessage("No se pudo cargar el tratamiento activo.")
            is ActiveTreatmentsSection.Loaded ->
                if (treatments.isEmpty()) {
                    PanelMessage("Sin tratamiento activo. Se registra al firmar una evolucion con prescripciones.")
                } else {
                    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        treatments.forEach { treatment ->
                            TreatmentRow(treatment, onSuspend = onSuspend?.let { { it(treatment) } })
                        }
                    }
                }
        }
    }
}

@Composable
private fun TreatmentRow(treatment: ActiveTreatment, onSuspend: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(HissRadiusDefault),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HissFaint),
        modifier = Modifier.fillMaxWidth().testTag("treatment_${treatment.id}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(treatment.drugName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(treatment.dose, treatment.frequency, treatment.route?.toShortLabel()).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                treatment.episodeStartDate?.let {
                    Text(
                        "Hospitalizacion del ${formatDate(it)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = HissInk2,
                    )
                }
            }
            if (onSuspend != null) {
                OutlinedButton(
                    onClick = onSuspend,
                    shape = RoundedCornerShape(HissRadiusDefault),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("treatment_suspend_${treatment.id}"),
                ) {
                    Text("Suspender", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun PanelMessage(text: String) {
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

@Composable
fun SuspendTreatmentDialog(
    suspension: TreatmentSuspension,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suspender · ${suspension.treatment.drugName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = suspension.reason,
                    onValueChange = onReasonChange,
                    label = { RequiredFieldLabel("Motivo de la suspension") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("treatment_suspend_reason_field"),
                )
                Text(
                    "La suspension queda registrada con fecha de hoy.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HissInk2,
                )
                if (suspension.error != null) {
                    Text(suspension.error, fontSize = 12.sp, color = HissWarn)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !suspension.isSaving,
                modifier = Modifier.testTag("treatment_suspend_confirm_button"),
            ) { Text("Suspender tratamiento") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !suspension.isSaving,
                modifier = Modifier.testTag("treatment_suspend_dismiss_button"),
            ) { Text("Cancelar") }
        },
    )
}
