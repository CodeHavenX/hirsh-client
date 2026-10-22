package com.cramsan.hirsh.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class Item(val id: String)

/** Same shape as [com.cramsan.hirsh.repository.KtorAdmissionCatalogRepositoryTest]'s `mockClient`. */
private fun mockClient(
    recorded: MutableList<Url>,
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

private fun MockRequestHandleScope.pageResponse(ids: List<String>, number: Int, totalPages: Int): HttpResponseData =
    respond(
        """{"content": [${ids.joinToString(",") { """{"id": "$it"}""" }}],
           "page": {"number": $number, "size": 100, "totalElements": 0, "totalPages": $totalPages}}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
    )

class PaginationTest {

    @Test
    fun `a single page takes one request for page 0 at the shared page size`() = runTest {
        val recorded = mutableListOf<Url>()
        val client = mockClient(recorded) { pageResponse(listOf("a", "b"), 0, 1) }

        val items = client.getAllPages<Item>("/api/v1/things")

        assertEquals(listOf(Item("a"), Item("b")), items)
        assertEquals(1, recorded.size)
        assertEquals("/api/v1/things", recorded.single().encodedPath)
        assertEquals("0", recorded.single().parameters["page"])
        assertEquals("$ALL_PAGES_PAGE_SIZE", recorded.single().parameters["size"])
    }

    @Test
    fun `every page is fetched in order and concatenated`() = runTest {
        val recorded = mutableListOf<Url>()
        val client = mockClient(recorded) { request ->
            when (val page = request.url.parameters["page"]!!.toInt()) {
                0 -> pageResponse(listOf("a", "b"), page, 3)
                1 -> pageResponse(listOf("c", "d"), page, 3)
                else -> pageResponse(listOf("e"), page, 3)
            }
        }

        val items = client.getAllPages<Item>("/api/v1/things")

        assertEquals(listOf("a", "b", "c", "d", "e"), items.map { it.id })
        assertEquals(listOf("0", "1", "2"), recorded.map { it.parameters["page"] })
    }

    @Test
    fun `filters are sent with every page request`() = runTest {
        val recorded = mutableListOf<Url>()
        val client = mockClient(recorded) { request ->
            val page = request.url.parameters["page"]!!.toInt()
            pageResponse(listOf("p$page"), page, 2)
        }

        client.getAllPages<Item>("/api/v1/things") { parameter("status", "ACTIVE") }

        assertEquals(listOf("ACTIVE", "ACTIVE"), recorded.map { it.parameters["status"] })
    }

    @Test
    fun `an empty collection takes one request and returns nothing`() = runTest {
        val recorded = mutableListOf<Url>()
        val client = mockClient(recorded) { pageResponse(emptyList(), 0, 0) }

        assertEquals(emptyList(), client.getAllPages<Item>("/api/v1/things"))
        assertEquals(1, recorded.size)
    }
}
