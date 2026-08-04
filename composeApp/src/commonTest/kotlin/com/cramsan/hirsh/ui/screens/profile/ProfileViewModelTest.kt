package com.cramsan.hirsh.ui.screens.profile

import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.repository.AuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeAuthRepository : AuthRepository {
    var logoutCalls = 0
        private set

    override suspend fun login(username: String, password: String): Result<Session> =
        error("not used by ProfileViewModel")

    override fun restoreSession(): Session? = null

    override fun logout() {
        logoutCalls++
    }
}

class ProfileViewModelTest {

    @Test
    fun `signOut delegates to the auth repository`() {
        val repository = FakeAuthRepository()
        val viewModel = ProfileViewModel(repository)

        viewModel.signOut()

        assertEquals(1, repository.logoutCalls)
    }
}
