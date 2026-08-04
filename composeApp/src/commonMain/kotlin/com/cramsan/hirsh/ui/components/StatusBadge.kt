package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissAccentWash
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissWarn
import com.cramsan.hirsh.ui.theme.HissWarnWash

/**
 * Tone for [StatusBadge], mirroring the `.badge-done` / `.badge-warn` /
 * `.badge-prog` / `.badge-off` variants in prototype/shared/styles.css.
 */
enum class BadgeTone {
    Done,
    Warn,
    Progress,
    Off,
}

private data class BadgeStyle(
    val text: Color,
    val border: Color,
    val fill: Color,
    val dashed: Boolean,
)

private fun styleFor(tone: BadgeTone): BadgeStyle = when (tone) {
    BadgeTone.Done -> BadgeStyle(HissAccent, HissAccent, HissAccentWash, dashed = false)
    BadgeTone.Warn -> BadgeStyle(HissWarn, HissWarn, HissWarnWash, dashed = false)
    BadgeTone.Progress -> BadgeStyle(HissWarn, HissWarn, HissWarnWash, dashed = true)
    BadgeTone.Off -> BadgeStyle(HissInk2, HissInk, Color.Transparent, dashed = true)
}

/**
 * One generic badge covering hospitalization estado, HC completion, allergy
 * warning, and account status -- mirrors `.badge` plus its tone variants in
 * prototype/shared/styles.css, rather than four bespoke composables.
 */
@Composable
fun StatusBadge(
    text: String,
    tone: BadgeTone,
) {
    val style = styleFor(tone)
    val cornerRadius = 6.dp
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = style.text,
        modifier = Modifier
            .background(style.fill, RoundedCornerShape(cornerRadius))
            .drawBehind {
                val strokeWidthPx = 1.dp.toPx()
                val pathEffect = if (style.dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                } else {
                    null
                }
                drawRoundRect(
                    color = style.border,
                    style = Stroke(width = strokeWidthPx, pathEffect = pathEffect),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                )
            }
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
