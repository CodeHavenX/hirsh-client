package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.AccessAction
import com.cramsan.hirsh.model.AccessLogEntry
import com.cramsan.hirsh.model.shortUserId
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.dto.AccessLogEntryDto
import com.cramsan.hirsh.network.dto.toDomain
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val PATIENT_ID = "8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10"

// Shape copied from the backend's AccessLogEntry schema, including the fields this client ignores.
private const val PAGE_JSON = """
{
  "content": [
    {
      "id": "7c8d9e0f-1a2b-4c3d-8e4f-5a6b7c8d9e0f",
      "userId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
      "patientId": "$PATIENT_ID",
      "action": "PATIENT_MODIFIED",
      "resource": "PATIENT",
      "detail": {"method": "PATCH", "path": "/api/v1/patients/$PATIENT_ID"},
      "sourceIp": "10.20.30.41",
      "userAgent": "Ktor client",
      "occurredAt": "2026-06-21T11:05:00Z"
    },
    {
      "id": "8d9e0f1a-2b3c-4d5e-9f6a-7b8c9d0e1f2a",
      "action": "SOMETHING_NEW",
      "occurredAt": "2026-06-20T08:00:00Z"
    }
  ],
  "page": {"number": 0, "size": 50, "totalElements": 132, "totalPages": 3}
}
"""

private fun clientResponding(
    status: HttpStatusCode,
    body: String,
    onRequest: (HttpRequestData) -> Unit = {},
): HttpClient = HttpClient(
    MockEngine { request ->
        onRequest(request)
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    },
) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    installApiErrorValidator(onUnauthorized = {})
}

class KtorAccessLogRepositoryTest {

    @Test
    fun `requests only the first page of the patient's trail`() = runTest {
        var requested: HttpRequestData? = null
        val repository = KtorAccessLogRepository(clientResponding(HttpStatusCode.OK, PAGE_JSON) { requested = it })

        repository.getPatientAccessLog(PATIENT_ID).first()

        val request = checkNotNull(requested)
        assertEquals("/api/v1/patients/$PATIENT_ID/access-logs", request.url.encodedPath)
        assertEquals("0", request.url.parameters["page"])
        assertEquals(ACCESS_LOG_PAGE_SIZE.toString(), request.url.parameters["size"])
    }

    @Test
    fun `maps entries and keeps the server's total count`() = runTest {
        val repository = KtorAccessLogRepository(clientResponding(HttpStatusCode.OK, PAGE_JSON))

        val page = repository.getPatientAccessLog(PATIENT_ID).first()

        assertEquals(132, page.totalEntries)
        assertEquals(2, page.entries.size)
        val modified = page.entries.first()
        assertEquals("3f2504e0-4f89-41d3-9a0c-0305e82c3301", modified.userId)
        assertEquals(AccessAction.PATIENT_MODIFIED, modified.action)
        assertEquals("10.20.30.41", modified.sourceIp)
    }

    @Test
    fun `an action this client doesn't know maps to OTHER instead of failing the whole page`() = runTest {
        val repository = KtorAccessLogRepository(clientResponding(HttpStatusCode.OK, PAGE_JSON))

        val unknown = repository.getPatientAccessLog(PATIENT_ID).first().entries[1]

        assertEquals(AccessAction.OTHER, unknown.action)
        assertEquals(null, unknown.userId)
    }

    @Test
    fun `a session without AUDIT_READ surfaces as ApiError Forbidden`() = runTest {
        val repository = KtorAccessLogRepository(clientResponding(HttpStatusCode.Forbidden, "{}"))

        val exception = assertFailsWith<ApiException> { repository.getPatientAccessLog(PATIENT_ID).first() }

        assertEquals(ApiError.Forbidden, exception.error)
    }

    @Test
    fun `occurredAt is converted to the given time zone and formatted for display`() {
        val dto = AccessLogEntryDto(id = "e1", action = "RECORD_READ", occurredAt = "2026-06-21T11:05:00Z")

        assertEquals("21 Jun 2026 · 06:05", dto.toDomain(TimeZone.of("America/Lima")).occurredAt)
        assertEquals("21 Jun 2026 · 11:05", dto.toDomain(TimeZone.UTC).occurredAt)
    }

    @Test
    fun `shortUserId shows the first 8 characters, or a dash when the entry has no user`() {
        val entry = AccessLogEntry("e1", "3f2504e0-4f89-41d3-9a0c-0305e82c3301", AccessAction.RECORD_READ, "", null)

        assertEquals("3f2504e0", entry.shortUserId())
        assertEquals("—", entry.copy(userId = null).shortUserId())
    }
}
