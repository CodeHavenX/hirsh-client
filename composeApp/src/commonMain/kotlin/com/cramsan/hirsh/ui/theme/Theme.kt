package com.cramsan.hirsh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = HissAccent,
    onPrimary = HissPaper,
    primaryContainer = HissAccentWash,
    onPrimaryContainer = HissAccent,
    secondary = HissSuccess,
    secondaryContainer = HissSuccessWash,
    error = HissWarn,
    errorContainer = HissWarnWash,
    background = HissPaper,
    onBackground = HissInk,
    surface = HissPaper,
    onSurface = HissInk,
    surfaceVariant = HissFaint,
    outline = HissFaint,
)

private val DarkColors = darkColorScheme(
    primary = HissAccent,
    onPrimary = HissPaper,
    secondary = HissSuccess,
    error = HissWarn,
)

/**
 * The Surface paints the theme's background and sets its text color: nothing else does, so without
 * it a dark-mode window showed through behind black text (HCEM-83). It takes its content's size, so
 * a component preview stays as small as the component; a screen fills the window itself.
 */
@Composable
fun HirshTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (useDarkTheme) DarkColors else LightColors) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
