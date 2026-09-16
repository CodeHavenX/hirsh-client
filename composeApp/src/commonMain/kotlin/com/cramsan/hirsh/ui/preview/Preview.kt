package com.cramsan.hirsh.ui.preview

import androidx.compose.ui.tooling.preview.AndroidUiModes
import androidx.compose.ui.tooling.preview.Preview as ComposePreview

/**
 * Phone/Tablet/Desktop layouts, plus a dark-mode pass of the Desktop layout -- 4 variants from one
 * annotation, no wrapper composable or `PreviewParameter` needed (see [PreviewComponent] for why
 * the dark pass works automatically, with no explicit `useDarkTheme` handling in the preview
 * body). Only Desktop gets a dark pass; stack a dark [ComposePreview] directly if a screen needs
 * Phone/Tablet dark coverage too.
 *
 * For a UI component rather than a full screen, use [PreviewComponent] instead -- components
 * don't need viewport-breakpoint coverage, just light/dark.
 */
@ComposePreview(name = "Phone", widthDp = 400, heightDp = 800)
@ComposePreview(name = "Tablet", widthDp = 820, heightDp = 1024)
@ComposePreview(name = "Desktop", widthDp = 1280, heightDp = 800)
@ComposePreview(name = "Desktop dark", widthDp = 1280, heightDp = 800, uiMode = AndroidUiModes.UI_MODE_NIGHT_YES or AndroidUiModes.UI_MODE_TYPE_NORMAL)
annotation class PreviewResponsive

/**
 * Light/dark variants for a UI component -- 2 variants from one annotation, no wrapper composable
 * needed. Use this instead of [PreviewResponsive] for a component (not a full screen), where
 * viewport-breakpoint coverage doesn't apply and only theme coverage matters.
 *
 * No `HirshTheme(useDarkTheme = ...)` override is needed in the preview body: Roborazzi's Compose
 * Desktop preview runner (`DefaultDesktopComposePreviewTester`) reads the `uiMode` bit off each
 * scanned preview and wraps rendering in `CompositionLocalProvider(LocalSystemTheme provides
 * SystemTheme.Dark)`, which is exactly what `isSystemInDarkTheme()` (and so `HirshTheme`'s default
 * `useDarkTheme`) reads on desktop. A plain `HirshTheme { content() }` with no explicit dark-mode
 * handling already renders correctly for both variants -- confirmed empirically against this
 * project's own Roborazzi Desktop pipeline, not just Android Studio's renderer.
 */
@ComposePreview(name = "Light")
@ComposePreview(name = "Dark", uiMode = AndroidUiModes.UI_MODE_NIGHT_YES or AndroidUiModes.UI_MODE_TYPE_NORMAL)
annotation class PreviewComponent
