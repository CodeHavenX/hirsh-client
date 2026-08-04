package com.cramsan.hirsh.ui.screens.login

import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAuthRepository(
    private val loginResult: Result<Session>,
    private val restoredSession: Session? = null,
) : AuthRepository {
    override suspend fun login(username: String, password: String): Result<Session> = loginResult
    override fun restoreSession(): Session? = restoredSession
    override fun logout() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects an already-restored session`() {
        val session = Session(username = "drpatel", displayName = "drpatel", role = Role.DOCTOR)
        val viewModel = LoginViewModel(FakeAuthRepository(Result.success(session), restoredSession = session))

        assertTrue(viewModel.uiState.value.loggedIn)
    }

    @Test
    fun `login success marks the user as logged in`() = runTest(dispatcher) {
        val session = Session(username = "drpatel", displayName = "drpatel", role = Role.DOCTOR)
        val viewModel = LoginViewModel(FakeAuthRepository(Result.success(session)))

        viewModel.login("drpatel", "hunter2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.loggedIn)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `login failure surfaces the error message and stays logged out`() = runTest(dispatcher) {
        val viewModel = LoginViewModel(
            FakeAuthRepository(Result.failure(IllegalArgumentException("Usuario o contrasena incorrectos"))),
        )

        viewModel.login("drpatel", "wrong")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loggedIn)
        assertFalse(state.isLoading)
        assertEquals("Usuario o contrasena incorrectos", state.error)
    }
}
