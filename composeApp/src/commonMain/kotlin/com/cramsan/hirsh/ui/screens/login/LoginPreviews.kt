package com.cramsan.hirsh.ui.screens.login

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.repository.AuthRepository
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme

private object PreviewAuthRepository : AuthRepository {
    override suspend fun login(username: String, password: String): Result<Session> =
        Result.success(Session(username = username, displayName = username, role = Role.DOCTOR))

    override fun restoreSession(): Session? = null

    override fun logout() = Unit
}

@Preview
@Composable
private fun LoginScreenPreview() {
    HirshTheme {
        LoginScreen(
            onLoggedIn = {},
            viewModel = LoginViewModel(PreviewAuthRepository),
        )
    }
}
