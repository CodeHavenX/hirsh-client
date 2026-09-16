package com.cramsan.hirsh.ui.screens.login

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme

@Preview
@Composable
private fun LoginScreenPreview() {
    HirshTheme {
        LoginScreenContent(
            uiState = LoginUiState(),
            username = "",
            onUsernameChange = {},
            password = "",
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}
