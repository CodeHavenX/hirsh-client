package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.preferences.AppPreferences

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<Session>
    fun restoreSession(): Session?
    fun logout()
}

/**
 * Stand-in until the backend service (separate repo) exposes a real auth endpoint.
 * Accepts any non-blank username/password, mirroring the prototype's login screen.
 */
class FakeAuthRepository(private val preferences: AppPreferences) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<Session> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Usuario o contrasena incorrectos"))
        }
        preferences.sessionUsername = username
        return Result.success(Session(username = username, displayName = username, role = Role.DOCTOR))
    }

    override fun restoreSession(): Session? {
        val username = preferences.sessionUsername ?: return null
        return Session(username = username, displayName = username, role = Role.DOCTOR)
    }

    override fun logout() {
        preferences.clearSession()
    }
}
