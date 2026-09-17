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
 */
fun HttpClientConfig<*>.installApiErrorValidator() {
    HttpResponseValidator {
        validateResponse { response ->
            if (!response.status.isSuccess()) {
                val problemDetails = try {
                    response.body<ProblemDetails>()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                val apiError = problemDetails?.toApiError(response.status)
                    ?: ApiError.Unknown(response.status.description)
                throw ApiException(apiError)
            }
        }
    }
}
