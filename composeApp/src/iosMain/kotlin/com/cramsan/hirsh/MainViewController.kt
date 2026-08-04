package com.cramsan.hirsh

import androidx.compose.ui.window.ComposeUIViewController
import com.cramsan.hirsh.di.initKoin
import platform.UIKit.UIViewController

private var koinStarted = false

fun MainViewController(): UIViewController {
    if (!koinStarted) {
        initKoin()
        koinStarted = true
    }
    return ComposeUIViewController { App() }
}
