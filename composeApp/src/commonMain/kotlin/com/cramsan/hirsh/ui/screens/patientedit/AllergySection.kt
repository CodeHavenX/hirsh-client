package com.cramsan.hirsh.ui.screens.patientedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.ui.components.AddAllergyButton
import com.cramsan.hirsh.ui.components.AllergyActions
import com.cramsan.hirsh.ui.components.AllergyDraftForm
import com.cramsan.hirsh.ui.components.AllergyList
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.theme.HissInk2

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
            AllergyDraftForm(draft = draft, isBusy = uiState.isAllergyBusy, actions = actions, lockAgent = true)
        } else {
            AddAllergyButton(enabled = !uiState.isAllergyBusy, onClick = actions.onStartAdd)
        }

        uiState.allergyError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("allergy_error"))
        }
    }
}
