package com.cramsan.hirsh

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.cramsan.hirsh.di.initKoin

fun main() {
    initKoin()
    application {
        Window(onCloseRequest = ::exitApplication, title = "HISS") {
            App()
        }
    }
}
