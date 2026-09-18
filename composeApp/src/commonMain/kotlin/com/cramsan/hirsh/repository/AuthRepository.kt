package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.dto.LoginRequest
import com.cramsan.hirsh.network.dto.LoginResponse
import com.cramsan.hirsh.network.dto.MeResponse
import com.cramsan.hirsh.network.dto.UserProfile
import com.cramsan.hirsh.preferences.AppPreferences
import com.cramsan.hirsh.util.Clock
import com.cramsan.hirsh.util.formatDate
import com.cramsan.hirsh.util.formatTime
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<Session>
    suspend fun restoreSession(): Session?
    suspend fun logout()
}

private const val INVALID_CREDENTIALS_MESSAGE = "Usuario o contrasena incorrectos"

/**
 * Real implementation, backed by the shared `HttpClient` (cookie jar + CSRF header from
 * HISS-601/602, error mapping from HISS-603/604 already installed on it). See
 * `network/dto/AuthDtos.kt` for the three DTOs mapped here, and that file's doc comments for
 * which backend schema (from the live service's own `/v3/api-docs`) each mirrors.
 *
 * Only implements the endpoints this ticket needs -- `GET /api/v1/auth/session` is a different
 * feature (an idle-timeout warning, per its own doc), not a restore pre-check the way the
 * ticket's original description assumed; `restoreSession` calls `/me` directly instead, which
 * the backend's own docs say is exactly for "rebuild the session state after a refresh."
 */
class KtorAuthRepository(
    private val httpClient: HttpClient,
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<Session> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE))
        }
        return try {
            val response = httpClient.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }.body<LoginResponse>()
            Result.success(response.user.toSession())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.error is ApiError.Unauthorized) {
                Result.failure(IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE))
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun restoreSession(): Session? = try {
        httpClient.get("/api/v1/auth/me").body<MeResponse>().toSession()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        if (e.error is ApiError.Unauthorized) null else throw e
    }

    override suspend fun logout() {
        try {
            httpClient.post("/api/v1/auth/logout")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            // The backend's own docs: "Calling it without a session is not an error." Whatever
            // 401 this throws already ran installApiErrorValidator's onUnauthorized -> forceLogout,
            // and the caller is about to clear local session state regardless.
            if (e.error !is ApiError.Unauthorized) throw e
        }
    }
}

private fun UserProfile.toSession(): Session =
    Session(username = username, displayName = fullName, role = rolesToRole(roles))

private fun MeResponse.toSession(): Session =
    Session(username = username, displayName = fullName, role = rolesToRole(roles))

/**
 * Lossy stopgap, not a real fix: the backend's role codes (e.g. "PSYCHIATRIST") and effective
 * permissions don't correspond to this client's two-value [Role] enum at all -- HISS-612 replaces
 * [Role] with a real permissions-based model. Until then, only an ADMIN-ish role code maps to
 * [Role.ADMIN] (needed to keep the existing Cuentas nav gate working); everything else defaults
 * to [Role.DOCTOR], matching [FakeAuthRepository]'s own existing default for an unmatched account.
 */
private fun rolesToRole(roles: List<String>): Role =
    if (roles.any { it.contains("ADMIN", ignoreCase = true) }) Role.ADMIN else Role.DOCTOR

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

    override suspend fun restoreSession(): Session? {
        val username = preferences.sessionUsername ?: return null
        val account = accountRepository.accounts.value.find { it.username == username }
        return sessionFor(username, account)
    }

    override suspend fun logout() {
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
