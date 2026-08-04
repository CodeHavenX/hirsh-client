package com.cramsan.hirsh.ui.screens.profile

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
    private val _session = MutableStateFlow<Session?>(
        Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR),
    )
    override val session: StateFlow<Session?> = _session.asStateFlow()

    override suspend fun login(username: String, password: String): Result<Session> =
        error("not used by ProfileScreen")

    override fun logout() {
        _session.value = null
    }
}

@Preview
@Composable
private fun ProfileScreenPreview() {
    HirshTheme {
        ProfileScreen(
            onSignedOut = {},
            viewModel = ProfileViewModel(PreviewSessionRepository),
        )
    }
}
