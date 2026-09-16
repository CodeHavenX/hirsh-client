package com.cramsan.hirsh.ui.preview

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
