package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cramsan.hirsh.model.ScaleSeverity
import com.cramsan.hirsh.model.toDisplayLabel

/** A scale result's band, warmer as it gets more severe. */
@Composable
fun ScaleSeverityBadge(severity: ScaleSeverity, modifier: Modifier = Modifier) {
    val tone = when (severity) {
        ScaleSeverity.MINIMAL -> BadgeTone.Done
        ScaleSeverity.MILD -> BadgeTone.Neutral
        ScaleSeverity.MODERATE -> BadgeTone.Progress
        ScaleSeverity.MOD_SEVERE, ScaleSeverity.SEVERE -> BadgeTone.Warn
    }
    Box(modifier) { StatusBadge(text = severity.toDisplayLabel(), tone = tone) }
}

/** The red marker on a result that raised the suicide-risk alert. */
@Composable
fun SuicideAlertBadge(modifier: Modifier = Modifier) {
    Box(modifier) { StatusBadge(text = "Alerta riesgo suicida", tone = BadgeTone.Warn) }
}
