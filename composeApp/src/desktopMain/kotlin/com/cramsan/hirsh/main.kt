package com.cramsan.hirsh

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.cramsan.cmpbridge.DesktopBridgeServer
import com.cramsan.hirsh.di.initKoin

fun main() {
    initKoin()
    application {
        Window(onCloseRequest = ::exitApplication, title = "HISS") {
            val scope = rememberCoroutineScope()
            DesktopBridgeServer.startIfEnabled(window, scope)
            App()
        }
    }
}
