package com.cramsan.hirsh.ui.screens.profile

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
import kotlin.test.assertNull

private val sampleSession = Session(username = "drpatel", displayName = "Dr. A. Patel", role = Role.DOCTOR)

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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

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
    fun `signOut delegates to the session repository and clears the shared session`() {
        val repository = FakeSessionRepository(initialSession = sampleSession)
        val viewModel = ProfileViewModel(repository)

        viewModel.signOut()

        assertEquals(1, repository.logoutCalls)
        assertNull(repository.session.value)
    }

    @Test
    fun `uiState reflects the session repository's current session`() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(FakeSessionRepository(initialSession = sampleSession))

        viewModel.uiState.test {
            awaitItem() // stateIn's synthetic initial value, before the real combine() emission
            assertEquals(sampleSession, awaitItem().session)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updatePassword blocks with an error when a required field is blank`() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(FakeSessionRepository(initialSession = sampleSession))
        viewModel.onCurrentPasswordChange("hunter2")
        viewModel.onNewPasswordChange("newpassword")

        viewModel.uiState.test {
            awaitItem()
            viewModel.updatePassword()
            val state = awaitItem()
            assertEquals("Completa los campos requeridos", state.error)
            assertEquals(false, state.updated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updatePassword blocks when newPassword is under 8 characters`() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(FakeSessionRepository(initialSession = sampleSession))
        viewModel.onCurrentPasswordChange("hunter2")
        viewModel.onNewPasswordChange("short")
        viewModel.onConfirmPasswordChange("short")

        viewModel.uiState.test {
            awaitItem()
            viewModel.updatePassword()
            val state = awaitItem()
            assertEquals("La nueva contrasena debe tener al menos 8 caracteres", state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updatePassword blocks when newPassword and confirmPassword do not match`() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(FakeSessionRepository(initialSession = sampleSession))
        viewModel.onCurrentPasswordChange("hunter2")
        viewModel.onNewPasswordChange("newpassword")
        viewModel.onConfirmPasswordChange("different")

        viewModel.uiState.test {
            awaitItem()
            viewModel.updatePassword()
            val state = awaitItem()
            assertEquals("Las contrasenas no coinciden", state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updatePassword succeeds, clears the form, and sets updated`() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(FakeSessionRepository(initialSession = sampleSession))
        viewModel.onCurrentPasswordChange("hunter2")
        viewModel.onNewPasswordChange("newpassword")
        viewModel.onConfirmPasswordChange("newpassword")

        viewModel.uiState.test {
            awaitItem()
            viewModel.updatePassword()
            val state = awaitItem()
            assertEquals(true, state.updated)
            assertNull(state.error)
            assertEquals("", state.currentPassword)
            assertEquals("", state.newPassword)
            assertEquals("", state.confirmPassword)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
