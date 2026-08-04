package com.cramsan.hirsh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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

@Composable
fun HirshTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content,
    )
}
