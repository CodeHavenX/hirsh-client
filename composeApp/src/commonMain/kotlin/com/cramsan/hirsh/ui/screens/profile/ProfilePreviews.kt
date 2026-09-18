package com.cramsan.hirsh.ui.screens.profile

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.ui.preview.PreviewResponsive
import com.cramsan.hirsh.ui.theme.HirshTheme

@PreviewResponsive
@Composable
private fun ProfileScreenPreview() {
    HirshTheme {
        ProfileScreenContent(
            uiState = ProfileUiState(
                session = Session(username = "drpatel", displayName = "Dr. A. Patel", roles = listOf("PSYCHIATRIST")),
            ),
            onCurrentPasswordChange = {},
            onNewPasswordChange = {},
            onConfirmPasswordChange = {},
            onUpdatePassword = {},
            onSignOut = {},
        )
    }
}
