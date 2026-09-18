package com.cramsan.hirsh.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Installs the response-pipeline step that parses a non-2xx response's RFC7807 body into an
 * [ApiError] and throws it as an [ApiException] -- see [ProblemDetails.toApiError] for the mapping
 * and `di/AppModule.kt` for where this is installed on the shared `HttpClient`.
 *
 * [onUnauthorized] runs before the throw whenever the mapped error is [ApiError.Unauthorized] (a
 * session-expiry 401) -- `di/AppModule.kt` wires this to [com.cramsan.hirsh.repository.SessionRepository.forceLogout]
 * via a Koin lambda that isn't resolved until a 401 actually happens, rather than this `HttpClient`
 * depending on `SessionRepository` directly: `SessionRepository`/`AuthRepository` will need this
 * same `HttpClient` for real login/logout calls once HISS-611 lands, and a direct dependency in
 * both directions would be a real constructor-injection cycle.
 */
fun HttpClientConfig<*>.installApiErrorValidator(onUnauthorized: suspend () -> Unit) {
    HttpResponseValidator {
        validateResponse { response ->
            if (!response.status.isSuccess()) {
                // A real 401 from this backend's default Spring Security entry point has no body
                // at all (Content-Length: 0, not even `{}`) -- response.body<ProblemDetails>()
                // throws on that, not just on malformed JSON. Falling back to an empty
                // ProblemDetails() rather than null keeps toApiError's status-code-first mapping
                // in charge either way, so a bodyless 401 still maps to Unauthorized instead of
                // silently falling through to Unknown and never triggering onUnauthorized.
                val problemDetails = try {
                    response.body<ProblemDetails>()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ProblemDetails()
                }
                val apiError = problemDetails.toApiError(response.status)
                if (apiError is ApiError.Unauthorized) {
                    onUnauthorized()
                }
                throw ApiException(apiError)
            }
        }
    }
}
