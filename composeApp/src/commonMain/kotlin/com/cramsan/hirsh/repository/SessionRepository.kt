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

    /**
     * True until [restore] has resolved once. [com.cramsan.hirsh.ui.navigation.AppNavHost] holds
     * off choosing a start destination until this flips to `false`, so a cold start doesn't
     * flash the login screen before a still-valid session has had a chance to restore (HISS-611).
     */
    val isRestoring: StateFlow<Boolean>

    suspend fun login(username: String, password: String): Result<Session>
    suspend fun logout()

    /**
     * Calls [AuthRepository.restoreSession] once and publishes the result -- a real session
     * restore is a network round-trip (`GET /api/v1/auth/me`), unlike the old
     * `AppPreferences.sessionUsername`-based check, so this can't run synchronously in the
     * constructor the way it used to. Call exactly once, from [com.cramsan.hirsh.ui.navigation.AppNavHost]'s
     * initial composition.
     */
    suspend fun restore()

    /**
     * Clears local session state only, without calling through to [AuthRepository.logout] --
     * for when the *server* already considers the session dead (a 401), so there's no point
     * making a network call of our own to tell it something it just told us.
     */
    fun forceLogout()
}

/**
 * Wraps [AuthRepository] rather than reaching past it into [com.cramsan.hirsh.preferences.AppPreferences]
 * directly -- [AuthRepository] already owns that persistence, so duplicating
 * it here would just be two places reading/writing the same state.
 */
class DefaultSessionRepository(
    private val authRepository: AuthRepository,
) : SessionRepository {

    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    private val _isRestoring = MutableStateFlow(true)
    override val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    override suspend fun login(username: String, password: String): Result<Session> =
        authRepository.login(username, password).onSuccess { _session.value = it }

    override suspend fun restore() {
        _session.value = authRepository.restoreSession()
        _isRestoring.value = false
    }

    override suspend fun logout() {
        authRepository.logout()
        _session.value = null
    }

    override fun forceLogout() {
        _session.value = null
    }
}
