package com.cramsan.hirsh.ui.components

import androidx.compose.ui.platform.ClipEntry

/** Compose has no common way to build a [ClipEntry]; each platform wraps plain text its own way. */
expect fun plainTextClipEntry(text: String): ClipEntry
