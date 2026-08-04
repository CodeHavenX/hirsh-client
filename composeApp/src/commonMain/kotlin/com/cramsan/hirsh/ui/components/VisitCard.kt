package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk2

/**
 * Clickable summary row used for both hospitalizations-in-a-patient
 * (`record.html`) and evoluciones-in-a-hospitalization
 * (`hospitalizacion.html`) lists -- mirrors the inline `.visit-card` markup
 * in those two pages (there's no single `render*` helper for it in
 * components.js, since both call sites build the markup by hand).
 */
@Composable
fun VisitCard(
    title: String,
    meta: String,
    summary: String,
    onClick: () -> Unit,
    active: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val borderColor = if (active) HissAccent else HissFaint
    val containerColor = if (active) HissAccentWash else MaterialTheme.colorScheme.surface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(meta, style = MaterialTheme.typography.labelSmall, color = HissInk2)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = HissInk2)
            }
            trailing()
        }
    }
}
