package com.cramsan.hirsh.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiErrorMappingTest {

    private fun buildClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine {
            respond(
                body,
                status,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            installApiErrorValidator()
        }
    }

    @Test
    fun unauthorizedMapsToUnauthorizedRegardlessOfBody() = runTest {
        val client = buildClient(HttpStatusCode.Unauthorized, "{}")

        val exception = assertFailsWith<ApiException> { client.get("http://localhost/x") }

        assertEquals(ApiError.Unauthorized, exception.error)
    }

    @Test
    fun conflictMapsToConflictWithResourceId() = runTest {
        val client = buildClient(
            HttpStatusCode.Conflict,
            """{"conflictingResourceId": "patient-123"}""",
        )

        val exception = assertFailsWith<ApiException> { client.get("http://localhost/x") }

        assertEquals(ApiError.Conflict("patient-123"), exception.error)
    }

    @Test
    fun badRequestWithFieldErrorsMapsToValidation() = runTest {
        val client = buildClient(
            HttpStatusCode.BadRequest,
            """{"errors": [{"field": "name", "message": "required"}, {"field": "dni", "message": "invalid"}]}""",
        )

        val exception = assertFailsWith<ApiException> { client.get("http://localhost/x") }

        val error = assertIs<ApiError.Validation>(exception.error)
        assertEquals(mapOf("name" to "required", "dni" to "invalid"), error.fields)
    }

    @Test
    fun badRequestWithNoFieldErrorsMapsToUnknownNotEmptyValidation() = runTest {
        val client = buildClient(HttpStatusCode.BadRequest, """{"title": "Bad request"}""")

        val exception = assertFailsWith<ApiException> { client.get("http://localhost/x") }

        assertIs<ApiError.Unknown>(exception.error)
    }

    @Test
    fun serverErrorWithNoUsefulBodyMapsToUnknown() = runTest {
        val client = buildClient(HttpStatusCode.InternalServerError, "not json at all")

        val exception = assertFailsWith<ApiException> { client.get("http://localhost/x") }

        assertIs<ApiError.Unknown>(exception.error)
    }

    @Test
    fun successfulResponseThrowsNothing() = runTest {
        val engine = MockEngine { respondOk("{}") }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            installApiErrorValidator()
        }

        val response = client.get("http://localhost/x")

        assertTrue(response.status.isSuccess())
    }
}
