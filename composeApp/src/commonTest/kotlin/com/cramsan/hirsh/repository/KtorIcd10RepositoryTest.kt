package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Icd10Code
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorIcd10RepositoryTest {

    @Test
    fun `search asks for psychiatric codes only and maps each entry`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(
                """
                {"content": [{"id": "9b1f", "code": "F32.2", "description": "Episodio depresivo grave sin síntomas psicóticos",
                  "chapter": "V"}], "page": {"number": 0, "size": 10, "totalElements": 1, "totalPages": 1}}
                """.trimIndent(),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            installApiErrorValidator(onUnauthorized = {})
        }

        val results = KtorIcd10Repository(client).search("depresivo")

        val url = requests.single().url
        assertEquals("/api/v1/icd10-codes", url.encodedPath)
        assertEquals("depresivo", url.parameters["search"])
        assertEquals("true", url.parameters["psychiatricOnly"])
        assertEquals(listOf(Icd10Code("F32.2", "Episodio depresivo grave sin síntomas psicóticos")), results)
    }
}
