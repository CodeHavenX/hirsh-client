package com.cramsan.hirsh.ui.screens.profile

import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeSessionRepository(
    initialSession: Session? = null,
) : SessionRepository {
    private val _session = MutableStateFlow(initialSession)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    var logoutCalls = 0
        private set

    override suspend fun login(username: String, password: String): Result<Session> =
        error("not used by ProfileViewModel")

    override fun logout() {
        logoutCalls++
        _session.value = null
    }
}

class ProfileViewModelTest {

    @Test
    fun `signOut delegates to the session repository and clears the shared session`() {
        val session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)
        val repository = FakeSessionRepository(initialSession = session)
        val viewModel = ProfileViewModel(repository)

        viewModel.signOut()

        assertEquals(1, repository.logoutCalls)
        assertNull(repository.session.value)
    }
}
