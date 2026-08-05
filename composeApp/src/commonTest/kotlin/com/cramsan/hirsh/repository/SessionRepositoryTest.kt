package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeAuthRepository(
    private val restoredSession: Session? = null,
    private val loginResult: Result<Session> = Result.failure(IllegalStateException("not stubbed")),
) : AuthRepository {
    var logoutCalls = 0
        private set

    override suspend fun login(username: String, password: String): Result<Session> = loginResult
    override fun restoreSession(): Session? = restoredSession
    override fun logout() {
        logoutCalls++
    }
}

class SessionRepositoryTest {

    @Test
    fun `initial session reflects what auth repository restores at startup`() {
        val session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)
        val repository = DefaultSessionRepository(FakeAuthRepository(restoredSession = session))

        assertEquals(session, repository.session.value)
    }

    @Test
    fun `initial session is null when nothing was restored`() {
        val repository = DefaultSessionRepository(FakeAuthRepository(restoredSession = null))

        assertNull(repository.session.value)
    }

    @Test
    fun `login success updates the shared session`() = runTest {
        val session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)
        val repository = DefaultSessionRepository(FakeAuthRepository(loginResult = Result.success(session)))

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
        val repository = DefaultSessionRepository(FakeAuthRepository(loginResult = failure))

        repository.session.test {
            assertNull(awaitItem())
            val result = repository.login("drpatel", "wrong")
            assertEquals(failure, result)
            expectNoEvents()
        }
    }

    @Test
    fun `logout delegates to auth repository and synchronously clears the shared session`() {
        val session = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)
        val authRepository = FakeAuthRepository(restoredSession = session)
        val repository = DefaultSessionRepository(authRepository)

        repository.logout()

        assertEquals(1, authRepository.logoutCalls)
        assertNull(repository.session.value)
    }
}
