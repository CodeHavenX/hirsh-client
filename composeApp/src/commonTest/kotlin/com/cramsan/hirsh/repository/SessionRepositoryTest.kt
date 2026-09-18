package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class StubAuthRepository(
    private val restoredSession: Session? = null,
    private val loginResult: Result<Session> = Result.failure(IllegalStateException("not stubbed")),
    private val logoutFailure: Exception? = null,
) : AuthRepository {
    var logoutCalls = 0
        private set

    override suspend fun login(username: String, password: String): Result<Session> = loginResult
    override suspend fun restoreSession(): Session? = restoredSession
    override suspend fun logout() {
        logoutCalls++
        logoutFailure?.let { throw it }
    }
}

class SessionRepositoryTest {

    @Test
    fun `isRestoring starts true and flips false once restore resolves`() = runTest {
        val repository = DefaultSessionRepository(StubAuthRepository())

        assertEquals(true, repository.isRestoring.value)
        repository.restore()
        assertEquals(false, repository.isRestoring.value)
    }

    @Test
    fun `restore populates session from what auth repository restores`() = runTest {
        val session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)
        val repository = DefaultSessionRepository(StubAuthRepository(restoredSession = session))

        repository.restore()

        assertEquals(session, repository.session.value)
    }

    @Test
    fun `session stays null when restore finds nothing`() = runTest {
        val repository = DefaultSessionRepository(StubAuthRepository(restoredSession = null))

        repository.restore()

        assertNull(repository.session.value)
    }

    @Test
    fun `session is null before restore is ever called`() {
        val session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)
        val repository = DefaultSessionRepository(StubAuthRepository(restoredSession = session))

        assertNull(repository.session.value)
    }

    @Test
    fun `login success updates the shared session`() = runTest {
        val session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)
        val repository = DefaultSessionRepository(StubAuthRepository(loginResult = Result.success(session)))

        repository.session.test {
            assertNull(awaitItem())
            val result = repository.login("drpatel", "hunter2")
            assertEquals(session, result.getOrNull())
            assertEquals(session, awaitItem())
        }
    }

    @Test
    fun `login failure leaves the shared session unchanged`() = runTest {
        val failure = Result.failure<Session>(IllegalArgumentException("Usuario o contrasena incorrectos"))
        val repository = DefaultSessionRepository(StubAuthRepository(loginResult = failure))

        repository.session.test {
            assertNull(awaitItem())
            val result = repository.login("drpatel", "wrong")
            assertEquals(failure, result)
            expectNoEvents()
        }
    }

    @Test
    fun `logout delegates to auth repository and clears the shared session`() = runTest {
        val session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)
        val authRepository = StubAuthRepository(restoredSession = session)
        val repository = DefaultSessionRepository(authRepository)
        repository.restore()

        repository.logout()

        assertEquals(1, authRepository.logoutCalls)
        assertNull(repository.session.value)
    }

    @Test
    fun `logout still clears the shared session when the network call fails`() = runTest {
        // A network failure (not the "already had no session" case KtorAuthRepository itself
        // treats as success) must not leave the caller stuck logged in client-side, nor propagate
        // uncaught -- ProfileViewModel.signOut() calls this from a plain viewModelScope.launch
        // with no catch of its own.
        val session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)
        val authRepository = StubAuthRepository(restoredSession = session, logoutFailure = RuntimeException("network down"))
        val repository = DefaultSessionRepository(authRepository)
        repository.restore()

        repository.logout()

        assertEquals(1, authRepository.logoutCalls)
        assertNull(repository.session.value)
    }

    @Test
    fun `forceLogout clears the shared session without calling auth repository`() {
        val session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)
        val authRepository = StubAuthRepository(restoredSession = session)
        val repository = DefaultSessionRepository(authRepository)

        repository.forceLogout()

        assertEquals(0, authRepository.logoutCalls)
        assertNull(repository.session.value)
    }
}
