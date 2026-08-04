package com.cramsan.hirsh.ui.screens.login

import app.cash.turbine.test
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeSessionRepository(
    private val loginResult: Result<Session>,
    initialSession: Session? = null,
) : SessionRepository {
    private val _session = MutableStateFlow(initialSession)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    override suspend fun login(username: String, password: String): Result<Session> =
        loginResult.onSuccess { _session.value = it }

    override fun logout() {
        _session.value = null
    }
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
        val viewModel = LoginViewModel(FakeSessionRepository(Result.success(session), initialSession = session))

        assertTrue(viewModel.uiState.value.loggedIn)
    }

    @Test
    fun `login success marks the user as logged in`() = runTest(dispatcher) {
        val session = Session(username = "drpatel", displayName = "drpatel", role = Role.DOCTOR)
        val viewModel = LoginViewModel(FakeSessionRepository(Result.success(session)))

        viewModel.uiState.test {
            assertEquals(LoginUiState(), awaitItem())
            viewModel.login("drpatel", "hunter2")
            assertEquals(LoginUiState(isLoading = true), awaitItem())
            assertEquals(LoginUiState(loggedIn = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login failure surfaces the error message and stays logged out`() = runTest(dispatcher) {
        val viewModel = LoginViewModel(
            FakeSessionRepository(Result.failure(IllegalArgumentException("Usuario o contrasena incorrectos"))),
        )

        viewModel.uiState.test {
            assertEquals(LoginUiState(), awaitItem())
            viewModel.login("drpatel", "wrong")
            assertEquals(LoginUiState(isLoading = true), awaitItem())
            assertEquals(LoginUiState(error = "Usuario o contrasena incorrectos"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
