package com.cramsan.hirsh.ui.preview

import androidx.compose.ui.tooling.preview.AndroidUiModes
import androidx.compose.ui.tooling.preview.Preview as ComposePreview

/**
 * Project-local wrapper over Compose Multiplatform's [ComposePreview] (real on all of
 * android/desktop/iOS/wasmJs as of Compose Multiplatform 1.10+, so no expect/actual is needed
 * here). `*Previews.kt` files import this name rather than the tooling annotation directly, so
 * shared preview configuration can be added here later without touching every call site.
 */
@ComposePreview
annotation class Preview

/**
 * Desktop-width preview canvas: above this project's 900dp narrow-nav breakpoint
 * (see `prototype/shared/styles.css`'s `@media (max-width: 900px)` rule), so the sidebar renders
 * expanded.
 */
@ComposePreview(name = "Desktop", widthDp = 1280, heightDp = 800)
annotation class PreviewDesktop

/**
 * Tablet-width preview canvas: just under this project's 900dp narrow-nav breakpoint, so the
 * sidebar renders collapsed.
 */
@ComposePreview(name = "Tablet", widthDp = 820, heightDp = 1024)
annotation class PreviewTablet

/** Phone-width preview canvas: well under the 900dp narrow-nav breakpoint. */
@ComposePreview(name = "Phone", widthDp = 400, heightDp = 800)
annotation class PreviewPhone

/**
 * Light-mode preview. Pair with a sibling function annotated [PreviewDark] -- see its doc for why
 * both the annotation *and* an explicit `HirshTheme(useDarkTheme = ...)` call are required.
 */
@ComposePreview(name = "Light")
annotation class PreviewLight

/**
 * Dark-mode preview.
 *
 * `uiMode` here makes Android Studio's own renderer show this correctly (it reads
 * `LocalConfiguration.uiMode`, which Studio derives from the annotation), but this project's
 * actual golden-image tests run on Compose Desktop (`generateComposePreviewDesktopTests`), and
 * Compose Desktop has no such config to derive `isSystemInDarkTheme()` from -- so the annotation
 * alone does nothing there.
 *
 * To get a real, distinct dark golden, the annotated function must *also* explicitly force it:
 *
 * ```
 * @PreviewLight
 * @Composable
 * private fun FooPreview() = HirshTheme(useDarkTheme = false) { Foo() }
 *
 * @PreviewDark
 * @Composable
 * private fun FooPreviewDark() = HirshTheme(useDarkTheme = true) { Foo() }
 * ```
 */
@ComposePreview(name = "Dark", uiMode = AndroidUiModes.UI_MODE_NIGHT_YES or AndroidUiModes.UI_MODE_TYPE_NORMAL)
annotation class PreviewDark
