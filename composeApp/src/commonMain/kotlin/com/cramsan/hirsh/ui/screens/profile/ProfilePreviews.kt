package com.cramsan.hirsh.ui.screens.profile

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.repository.AuthRepository
import com.cramsan.hirsh.ui.theme.HirshTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

private object PreviewAuthRepository : AuthRepository {
    override suspend fun login(username: String, password: String): Result<Session> =
        Result.success(Session(username = username, displayName = username, role = Role.DOCTOR))

    override fun restoreSession(): Session? = null

    override fun logout() = Unit
}

@Preview
@Composable
private fun ProfileScreenPreview() {
    HirshTheme {
        ProfileScreen(
            onSignedOut = {},
            viewModel = ProfileViewModel(PreviewAuthRepository),
        )
    }
}
