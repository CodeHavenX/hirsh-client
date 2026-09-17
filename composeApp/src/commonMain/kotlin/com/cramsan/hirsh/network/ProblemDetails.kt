package com.cramsan.hirsh.network

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/** Mirrors the backend's RFC7807 problem-details error body, plus its own extension fields. */
@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val errors: List<FieldError>? = null,
    val conflictingResourceId: String? = null,
) {
    @Serializable
    data class FieldError(val field: String, val message: String)
}

/**
 * Status-code-first mapping, following RFC7807 convention: 401 is always [ApiError.Unauthorized]
 * regardless of body content, 409 is always [ApiError.Conflict] (empty resource id if the backend
 * didn't include one), 400/422 map to [ApiError.Validation] only when [ProblemDetails.errors] is
 * actually populated (otherwise there's nothing field-level to surface, so it falls through to
 * [ApiError.Unknown] instead of a [ApiError.Validation] with an empty field map).
 */
fun ProblemDetails.toApiError(status: HttpStatusCode): ApiError = when {
    status == HttpStatusCode.Unauthorized -> ApiError.Unauthorized
    status == HttpStatusCode.Conflict -> ApiError.Conflict(conflictingResourceId.orEmpty())
    (status == HttpStatusCode.BadRequest || status == HttpStatusCode.UnprocessableEntity) && !errors.isNullOrEmpty() ->
        ApiError.Validation(errors.associate { it.field to it.message })
    else -> ApiError.Unknown(detail ?: title)
}
