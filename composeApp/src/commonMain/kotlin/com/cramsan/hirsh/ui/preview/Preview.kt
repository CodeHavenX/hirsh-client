package com.cramsan.hirsh.ui.preview

/**
 * On android/desktop this is the real `androidx.compose.ui.tooling.preview.Preview`, so IDE
 * previews and Roborazzi's preview scanner pick it up. Compose Multiplatform hasn't extended the
 * unified `@Preview` to iOS/wasmJs yet, so those targets get a no-op marker just to keep
 * `*Previews.kt` compiling everywhere from commonMain.
 */
expect annotation class Preview()
