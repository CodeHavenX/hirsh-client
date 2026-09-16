package com.cramsan.hirsh.ui.screens.profile

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme

@Preview
@Composable
private fun ProfileScreenPreview() {
    HirshTheme {
        ProfileScreenContent(
            uiState = ProfileUiState(
                session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR),
            ),
            onCurrentPasswordChange = {},
            onNewPasswordChange = {},
            onConfirmPasswordChange = {},
            onUpdatePassword = {},
            onSignOut = {},
        )
    }
}
