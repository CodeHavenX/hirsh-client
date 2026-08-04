package com.cramsan.hirsh

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.ui.navigation.AppNavHost
import com.cramsan.hirsh.ui.theme.HirshTheme

@Composable
fun App() {
    HirshTheme {
        AppNavHost()
    }
}
