package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2

/**
 * The open allergy form. [editingId] null means a new allergy. Shared by `EditPatientScreen`
 * (HISS-623), where every save goes straight to the allergies sub-resource, and
 * `RegisterPatientScreen` (HISS-625), where entries stay local until the patient is created.
 */
data class AllergyDraft(
    val editingId: String? = null,
    val allergyType: AllergyType? = null,
    val description: String = "",
    val severity: Severity? = null,
    val observations: String = "",
    /**
     * The edited allergy's severity as loaded. Once a saved allergy is graded, a PATCH can't reset
     * it back to ungraded (a null field means "unchanged" server-side), so the form doesn't offer that.
     */
    val originalSeverity: Severity? = null,
) {
    val isNew: Boolean get() = editingId == null
}

/** Every callback the allergy list + [AllergyDraftForm] need, bundled so each screen's signature stays readable. */
data class AllergyActions(
    val onStartAdd: () -> Unit = {},
    val onStartEdit: (Allergy) -> Unit = {},
    val onTypeChange: (AllergyType) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onSeverityChange: (Severity?) -> Unit = {},
    val onObservationsChange: (String) -> Unit = {},
    val onCancelDraft: () -> Unit = {},
    val onSaveDraft: () -> Unit = {},
    val onRequestDelete: (Allergy) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
)

private val allergyTypes = AllergyType.entries
private val severities: List<Severity?> = listOf(null) + Severity.entries

/**
 * Add/edit form for one allergy. [lockAgent] is for allergies already saved on the backend: the
 * real `PatchAllergyRequest` can't change type/description (see [Allergy]'s doc comment) or reset
 * a graded severity, so editing one only offers severity/observations. Local, not-yet-saved
 * entries (registration) pass `false` and stay fully editable.
 */
@Composable
fun AllergyDraftForm(draft: AllergyDraft, isBusy: Boolean, actions: AllergyActions, lockAgent: Boolean) {
    val agentLocked = lockAgent && !draft.isNew
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("allergy_form")) {
        if (agentLocked) {
            ReadOnlyField("Tipo", draft.allergyType?.toDisplayLabel().orEmpty())
            ReadOnlyField("Agente", draft.description)
        } else {
            SelectField(
                label = { RequiredFieldLabel("Tipo") },
                options = allergyTypes.map { it.toDisplayLabel() },
                selected = draft.allergyType?.toDisplayLabel().orEmpty(),
                onSelect = { label -> allergyTypes.firstOrNull { it.toDisplayLabel() == label }?.let(actions.onTypeChange) },
                testTag = "allergy_type_field",
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = actions.onDescriptionChange,
                label = { RequiredFieldLabel("Agente (ej: Penicilina)") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                shape = fieldShape,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("allergy_description_field"),
            )
        }
        val severityOptions = if (agentLocked && draft.originalSeverity != null) Severity.entries else severities
        SelectField(
            label = { Text("Severidad", fontSize = 12.sp, color = HissInk2) },
            options = severityOptions.map { it.toDisplayLabel() },
            selected = draft.severity.toDisplayLabel(),
            onSelect = { label -> actions.onSeverityChange(severityOptions.first { it.toDisplayLabel() == label }) },
            testTag = "allergy_severity_field",
        )
        OutlinedTextField(
            value = draft.observations,
            onValueChange = actions.onObservationsChange,
            label = { Text("Observaciones", fontSize = 12.sp, color = HissInk2) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
            shape = fieldShape,
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("allergy_observations_field"),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = actions.onCancelDraft,
                enabled = !isBusy,
                shape = fieldShape,
                border = BorderStroke(1.5.dp, HissInk),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("allergy_cancel_button"),
            ) {
                Text("Cancelar", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissInk)
            }
            Button(
                onClick = actions.onSaveDraft,
                enabled = !isBusy,
                shape = fieldShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.padding(start = 10.dp).testTag("allergy_save_button"),
            ) {
                Text(if (draft.isNew) "Agregar alergia" else "Guardar alergia", fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** "+ Agregar alergia" -- opens a blank [AllergyDraftForm]. */
@Composable
fun AddAllergyButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = fieldShape,
        border = BorderStroke(1.5.dp, HissInk),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.testTag("allergy_add_button"),
    ) {
        Text("+ Agregar alergia", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissInk)
    }
}
