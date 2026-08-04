package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissInk2

/**
 * Read-only label/value block used on evolucion-view and Historia Clinica
 * read screens -- mirrors `renderField` in prototype/shared/components.js.
 */
@Composable
fun ReadOnlyField(
    label: String,
    text: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = HissInk2,
        )
        Row(modifier = Modifier.padding(top = 4.dp).height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier.width(3.dp).fillMaxHeight().background(HissAccentWash),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
