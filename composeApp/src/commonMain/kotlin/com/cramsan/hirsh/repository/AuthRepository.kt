package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.dto.LoginRequest
import com.cramsan.hirsh.network.dto.LoginResponse
import com.cramsan.hirsh.network.dto.MeResponse
import com.cramsan.hirsh.network.dto.UserProfile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

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
    Session(username = username, displayName = fullName, roles = roles, permissions = permissions.toSet())

private fun MeResponse.toSession(): Session =
    Session(username = username, displayName = fullName, roles = roles, permissions = permissions.toSet())
