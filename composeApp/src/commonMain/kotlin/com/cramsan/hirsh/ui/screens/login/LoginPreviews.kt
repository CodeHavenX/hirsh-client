package com.cramsan.hirsh.ui.screens.login

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private object PreviewSessionRepository : SessionRepository {
    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    override suspend fun login(username: String, password: String): Result<Session> {
        val session = Session(username = username, displayName = username, role = Role.DOCTOR)
        _session.value = session
        return Result.success(session)
    }

    override fun logout() {
        _session.value = null
    }
}

@Preview
@Composable
private fun LoginScreenPreview() {
    HirshTheme {
        LoginScreen(
            onLoggedIn = {},
            viewModel = LoginViewModel(PreviewSessionRepository),
        )
    }
}
