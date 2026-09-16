package com.cramsan.hirsh

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cramsan.cmpbridge.DesktopBridgeServer
import com.cramsan.hirsh.di.initKoin

fun main() {
    initKoin()
    application {
        // Compose Desktop's own default (800x600) is narrower than AppScaffold's 900dp
        // narrow-nav breakpoint, so the app would open with the sidebar collapsed behind the
        // hamburger toggle on first launch -- not the intended default for a normal desktop
        // window. 1280x800 matches this project's own PreviewResponsive "Desktop" canvas size.
        val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        Window(onCloseRequest = ::exitApplication, title = "HISS", state = windowState) {
            val scope = rememberCoroutineScope()
            DesktopBridgeServer.startIfEnabled(window, scope)
            App()
        }
    }
}
