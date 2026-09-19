package com.cramsan.hirsh.network

/** Client-side mapping of the backend's RFC7807 problem-details error bodies. */
sealed interface ApiError {
    data class Validation(val fields: Map<String, String>) : ApiError
    data class Conflict(val resourceId: String) : ApiError
    data class NotFound(val resourceId: String? = null) : ApiError
    data object Unauthorized : ApiError
    data class Unknown(val message: String?) : ApiError
}

/** Thrown by the shared `HttpClient`'s response validator (`di/AppModule.kt`) on any non-2xx response. */
class ApiException(val error: ApiError) : Exception()
