package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place in the app that can answer "who's logged in, and what's
 * their role?" past the login screen -- shared by [com.cramsan.hirsh.ui.screens.login.LoginViewModel],
 * [com.cramsan.hirsh.ui.screens.profile.ProfileViewModel], and
 * [com.cramsan.hirsh.ui.navigation.AppNavHost] so a login/logout made through
 * any one of them is immediately visible to the others.
 *
 * Deliberately a separate interface from [AuthRepository] rather than an
 * extension of it -- adding a `session` property directly onto
 * `AuthRepository` would break every existing `AuthRepository` fake across
 * this repo's tests, the same way HISS-112 breaks `PatientRepository`'s.
 */
interface SessionRepository {
    val session: StateFlow<Session?>
    suspend fun login(username: String, password: String): Result<Session>
    fun logout()
}

/**
 * Wraps [AuthRepository] rather than reaching past it into [com.cramsan.hirsh.preferences.AppPreferences]
 * directly -- [AuthRepository] already owns that persistence, so duplicating
 * it here would just be two places reading/writing the same state.
 */
class DefaultSessionRepository(
    private val authRepository: AuthRepository,
) : SessionRepository {

    private val _session = MutableStateFlow(authRepository.restoreSession())
    override val session: StateFlow<Session?> = _session.asStateFlow()

    override suspend fun login(username: String, password: String): Result<Session> =
        authRepository.login(username, password).onSuccess { _session.value = it }

    override fun logout() {
        authRepository.logout()
        _session.value = null
    }
}
