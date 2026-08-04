package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusSmall

/**
 * Small mono pill used inside [EncounterTopBar]'s meta row and other
 * inline detail chips -- mirrors `.chip` in prototype/shared/styles.css.
 * Not named as its own component in HISS-107's scope, but every mock usage
 * of the encounter bar's meta area needs one; bundled here rather than
 * deferred to a later ticket.
 */
@Composable
fun Chip(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = HissInk2,
        modifier = Modifier
            .border(1.dp, HissFaint, RoundedCornerShape(HissRadiusSmall))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
