package com.cramsan.hirsh.ui.screens.patientedit

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.AllergyList
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.components.ReadOnlyField
import com.cramsan.hirsh.ui.components.RequiredFieldLabel
import com.cramsan.hirsh.ui.components.SelectField
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2

/** Every allergy callback [AllergySection] needs, bundled so `EditPatientScreenContent`'s signature stays readable. */
internal data class AllergyActions(
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
 * Allergy list + add/edit form. Every action here saves immediately against the allergies
 * sub-resource (HISS-623) -- the caption says so, since it's the one part of this screen
 * "Guardar cambios"/"Cancelar" don't apply to.
 */
@Composable
internal fun AllergySection(uiState: EditPatientUiState, actions: AllergyActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FormSectionCaption("Alergias conocidas")
        Text(
            "Cada cambio en alergias se guarda al instante, aparte de \"Guardar cambios\".",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = HissInk2,
        )
        AllergyList(
            allergies = uiState.allergies,
            enabled = !uiState.isAllergyBusy,
            confirmingDeleteId = uiState.confirmingDeleteId,
            onEdit = actions.onStartEdit,
            onRequestDelete = actions.onRequestDelete,
            onConfirmDelete = actions.onConfirmDelete,
            onCancelDelete = actions.onCancelDelete,
        )

        val draft = uiState.allergyDraft
        if (draft != null) {
            AllergyDraftForm(draft = draft, isBusy = uiState.isAllergyBusy, actions = actions)
        } else {
            OutlinedButton(
                onClick = actions.onStartAdd,
                enabled = !uiState.isAllergyBusy,
                shape = fieldShape,
                border = BorderStroke(1.5.dp, HissInk),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("allergy_add_button"),
            ) {
                Text("+ Agregar alergia", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissInk)
            }
        }

        uiState.allergyError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("allergy_error"))
        }
    }
}

@Composable
private fun AllergyDraftForm(draft: AllergyDraft, isBusy: Boolean, actions: AllergyActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("allergy_form")) {
        if (draft.isNew) {
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
        } else {
            // The agent itself can't be PATCHed (see Allergy's doc comment) -- correcting it
            // means removing this entry and adding a new one.
            ReadOnlyField("Tipo", draft.allergyType?.toDisplayLabel().orEmpty())
            ReadOnlyField("Agente", draft.description)
        }
        // Once graded, the real PATCH can't reset a severity back to ungraded (null means
        // "unchanged"), so that option is only offered while it's still true.
        val severityOptions = if (draft.originalSeverity != null) Severity.entries else severities
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
