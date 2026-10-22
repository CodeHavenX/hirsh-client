package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Same shape as [KtorPatientRepositoryTest]'s `mockClient`, recording only each request's URL. */
private fun mockClient(
    recorded: MutableList<Url> = mutableListOf(),
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient {
    val engine = MockEngine { request ->
        recorded += request.url
        handler(request)
    }
    return HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        installApiErrorValidator(onUnauthorized = {})
    }
}

private fun MockRequestHandleScope.jsonResponse(body: String): HttpResponseData =
    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

private fun pageJson(content: List<String>, number: Int, totalPages: Int): String =
    """{"content": [${content.joinToString(",")}], "page": {"number": $number, "size": 100, "totalElements": 0, "totalPages": $totalPages}}"""

private fun serviceJson(id: String, name: String, active: Boolean = true): String =
    """{"id": "$id", "code": "S-$id", "name": "$name", "type": "INPATIENT", "active": $active, "audit": {"jpaVersion": 0}}"""

private fun bedJson(id: String, number: String, status: String): String =
    """{"id": "$id", "number": "$number", "status": "$status", "currentOccupant": null}"""

private fun userJson(id: String, fullName: String): String =
    """{"id": "$id", "username": "u$id", "fullName": "$fullName", "status": "ACTIVE", "roles": []}"""

class KtorAdmissionCatalogRepositoryTest {

    @Test
    fun `services merges every page and leaves out inactive ones`() = runTest {
        val repository = KtorAdmissionCatalogRepository(
            mockClient { request ->
                when (request.url.parameters["page"]) {
                    "0" -> jsonResponse(pageJson(listOf(serviceJson("s1", "Psiquiatria"), serviceJson("s2", "Cerrado", active = false)), 0, 2))
                    else -> jsonResponse(pageJson(listOf(serviceJson("s3", "Emergencia")), 1, 2))
                }
            },
        )

        assertEquals(listOf(ServiceRef("s1", "Psiquiatria"), ServiceRef("s3", "Emergencia")), repository.services())
    }

    @Test
    fun `availableBeds reads the service's bed board and keeps only AVAILABLE beds`() = runTest {
        val recorded = mutableListOf<Url>()
        val repository = KtorAdmissionCatalogRepository(
            mockClient(recorded) {
                jsonResponse(
                    """
                    {"service": {"id": "s1", "name": "Psiquiatria"}, "occupancy": {"total": 4},
                     "beds": [${bedJson("b1", "01", "AVAILABLE")}, ${bedJson("b2", "02", "OCCUPIED")},
                              ${bedJson("b3", "03", "MAINTENANCE")}, ${bedJson("b4", "04", "RESERVED")},
                              ${bedJson("b5", "05", "AVAILABLE")}]}
                    """.trimIndent(),
                )
            },
        )

        assertEquals(listOf(BedRef("b1", "01"), BedRef("b5", "05")), repository.availableBeds("s1"))
        assertEquals("/api/v1/clinical-services/s1/beds", recorded.single().encodedPath)
    }

    @Test
    fun `attendingPhysicians asks for active attending physicians only`() = runTest {
        val recorded = mutableListOf<Url>()
        val repository = KtorAdmissionCatalogRepository(
            mockClient(recorded) { jsonResponse(pageJson(listOf(userJson("u1", "Ana Torres Quispe")), 0, 1)) },
        )

        assertEquals(listOf(UserRef("u1", "Ana Torres Quispe")), repository.attendingPhysicians())
        val url = recorded.single()
        assertEquals("/api/v1/users", url.encodedPath)
        assertEquals("ATTENDING_PHYSICIAN", url.parameters["roleCode"])
        assertEquals("ACTIVE", url.parameters["status"])
    }
}
