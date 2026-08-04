package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cramsan.hirsh.ui.theme.HissInk2

/**
 * Label/value row used in the patient record and hospitalization cards --
 * mirrors `renderKV` in prototype/shared/components.js.
 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
) {
    KeyValueRow(label) {
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * [KeyValueRow] overload for a non-text value, e.g. an allergy [StatusBadge]
 * (see `record.html`'s `renderKV('Alergias', ...)` call).
 */
@Composable
fun KeyValueRow(
    label: String,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = HissInk2)
        value()
    }
}
