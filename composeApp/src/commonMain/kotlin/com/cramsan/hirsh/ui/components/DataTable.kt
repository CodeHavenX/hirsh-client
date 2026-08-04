package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault

/**
 * One column of a [DataTable] -- mirrors an entry in the `columns` array
 * passed to `renderDataTable` in prototype/shared/components.js. `content`
 * is a Composable slot (not a string renderer) so callers can embed other
 * components -- e.g. a [StatusBadge] cell -- directly.
 */
data class DataTableColumn<T>(
    val label: String,
    val weight: Float = 1f,
    val content: @Composable (T) -> Unit,
)

/**
 * Column-driven row list -- mirrors `renderDataTable` in
 * prototype/shared/components.js. Renders the header and all rows in a
 * plain [Column]; callers that need virtualization/scrolling wrap it in
 * their own scrollable container.
 */
@Composable
fun <T> DataTable(
    columns: List<DataTableColumn<T>>,
    rows: List<T>,
    onRowClick: ((T) -> Unit)? = null,
    isHighlighted: (T) -> Boolean = { false },
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HissFaint, RoundedCornerShape(HissRadiusDefault)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(vertical = 10.dp, horizontal = 12.dp),
        ) {
            columns.forEach { column ->
                Text(
                    text = column.label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HissInk2,
                    modifier = Modifier.weight(column.weight),
                )
            }
        }
        rows.forEachIndexed { index, row ->
            var rowModifier = Modifier
                .fillMaxWidth()
                .background(if (isHighlighted(row)) HissAccentWash else MaterialTheme.colorScheme.surface)
            if (onRowClick != null) {
                rowModifier = rowModifier.clickable { onRowClick(row) }
            }
            rowModifier = rowModifier.padding(vertical = 12.dp, horizontal = 12.dp)
            Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                columns.forEach { column ->
                    Box(modifier = Modifier.weight(column.weight)) {
                        column.content(row)
                    }
                }
            }
            if (index != rows.lastIndex) {
                HorizontalDivider(color = HissFaint)
            }
        }
    }
}
