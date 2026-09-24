package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissWarn

/**
 * Severity -> badge tone. Ungraded ([Severity] null) gets the dashed "off" look rather than any
 * real grade's tone -- an allergy nobody has graded yet must never read as a mild one.
 */
fun Severity?.toBadgeTone(): BadgeTone = when (this) {
    Severity.SEVERE -> BadgeTone.Warn
    Severity.MODERATE -> BadgeTone.Progress
    Severity.MILD -> BadgeTone.Neutral
    null -> BadgeTone.Off
}

/**
 * One row per recorded allergy: agent, type, severity badge and any observations. Read-only
 * when [onEdit]/[onRequestDelete] are null (the patient record); editable on `EditPatientScreen`
 * (HISS-623), where removing asks for an inline confirmation first -- [confirmingDeleteId] is the
 * row currently asking. There's no prototype design for a per-allergy list (the mocks only have a
 * single free-text field), so this is built from the existing badge/link vocabulary instead.
 */
@Composable
fun AllergyList(
    allergies: List<Allergy>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    confirmingDeleteId: String? = null,
    onEdit: ((Allergy) -> Unit)? = null,
    onRequestDelete: ((Allergy) -> Unit)? = null,
    onConfirmDelete: (() -> Unit)? = null,
    onCancelDelete: (() -> Unit)? = null,
) {
    if (allergies.isEmpty()) {
        Row(modifier = modifier.testTag("allergy_list_empty")) {
            StatusBadge(text = "Ninguna", tone = BadgeTone.Off)
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        allergies.forEachIndexed { index, allergy ->
            if (index > 0) HorizontalDivider(color = HissFaint)
            AllergyRow(
                allergy = allergy,
                index = index,
                enabled = enabled,
                isConfirmingDelete = allergy.id == confirmingDeleteId,
                onEdit = onEdit,
                onRequestDelete = onRequestDelete,
                onConfirmDelete = onConfirmDelete,
                onCancelDelete = onCancelDelete,
            )
        }
    }
}

@Composable
private fun AllergyRow(
    allergy: Allergy,
    index: Int,
    enabled: Boolean,
    isConfirmingDelete: Boolean,
    onEdit: ((Allergy) -> Unit)?,
    onRequestDelete: ((Allergy) -> Unit)?,
    onConfirmDelete: (() -> Unit)?,
    onCancelDelete: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("allergy_row_$index"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(allergy.description, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    allergy.allergyType.toDisplayLabel().uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HissInk2,
                )
            }
            StatusBadge(text = allergy.severity.toDisplayLabel(), tone = allergy.severity.toBadgeTone())
        }
        if (allergy.observations.isNotBlank()) {
            Text(allergy.observations, style = MaterialTheme.typography.bodySmall, color = HissInk2)
        }
        if (onEdit != null || onRequestDelete != null) {
            Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isConfirmingDelete) {
                    Text("¿Quitar esta alergia?", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = HissWarn)
                    onConfirmDelete?.let { ActionLink("Quitar", HissWarn, enabled, "allergy_confirm_delete_$index", it) }
                    onCancelDelete?.let { ActionLink("Cancelar", HissInk2, enabled, "allergy_cancel_delete_$index", it) }
                } else {
                    onEdit?.let { ActionLink("Editar", HissAccent, enabled, "allergy_edit_$index") { it(allergy) } }
                    onRequestDelete?.let { ActionLink("Quitar", HissWarn, enabled, "allergy_delete_$index") { it(allergy) } }
                }
            }
        }
    }
}

/** Same monospace text-link look as `PatientRecordScreen`'s "Ver historial de cambios →". */
@Composable
private fun ActionLink(text: String, color: Color, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = if (enabled) color else HissFaint,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).testTag(tag),
    )
}
