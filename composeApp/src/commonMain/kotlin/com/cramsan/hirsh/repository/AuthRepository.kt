package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.preferences.AppPreferences
import com.cramsan.hirsh.util.Clock
import com.cramsan.hirsh.util.formatDate
import com.cramsan.hirsh.util.formatTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<Session>
    fun restoreSession(): Session?
    fun logout()
}

private const val INVALID_CREDENTIALS_MESSAGE = "Usuario o contrasena incorrectos"

/**
 * Stand-in until the backend service (separate repo) exposes a real auth endpoint.
 * Accepts any non-blank username/password not tied to an inactive seeded account,
 * mirroring the prototype's login screen (which checks no username list at all) --
 * an unmatched username still succeeds with a default [Role.DOCTOR] session, exactly
 * as before this looked accounts up at all. Only a username that matches a seeded,
 * inactive [Account] gets rejected.
 */
class FakeAuthRepository(
    private val preferences: AppPreferences,
    private val accountRepository: AccountRepository,
    private val clock: Clock,
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<Session> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE))
        }
        val account = accountRepository.accounts.value.find { it.username == username }
        if (account != null && account.status == AccountStatus.INACTIVE) {
            return Result.failure(IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE))
        }
        preferences.sessionUsername = username
        if (account != null) {
            accountRepository.updateLastLogin(username, nowFormatted())
        }
        return Result.success(sessionFor(username, account))
    }

    override fun restoreSession(): Session? {
        val username = preferences.sessionUsername ?: return null
        val account = accountRepository.accounts.value.find { it.username == username }
        return sessionFor(username, account)
    }

    override fun logout() {
        preferences.clearSession()
    }

    private fun sessionFor(username: String, account: Account?): Session = if (account != null) {
        Session(username = username, displayName = account.name, role = account.role)
    } else {
        Session(username = username, displayName = username, role = Role.DOCTOR)
    }

    private fun nowFormatted(): String {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "${formatDate(now.date)} ${formatTime(now.time)}"
    }
}
