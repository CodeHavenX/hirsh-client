package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Only [latestVersionId] matters to an export. */
private class VersionedNotes : ClinicalNoteRepository {
    override fun getHistoriaClinica(hospId: String): Flow<HistoriaClinica> = emptyFlow()
    override fun getEvoluciones(hospId: String): Flow<List<Evolucion>> = emptyFlow()
    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> = flowOf(null)
    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit
    override suspend fun signHistoriaClinica(hospId: String) = Unit
    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
    override suspend fun signEvolucion(hospId: String, evoId: String) = Unit
    override suspend fun latestVersionId(hospId: String, evoId: String?): String =
        if (evoId == null) "hc-version" else "$evoId-version"
}

private val PDF_BYTES = "%PDF-1.4 fake".encodeToByteArray()

class KtorPdfExportRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()

    /** What each status check answers, in order; the last one repeats. */
    private val statuses = ArrayDeque(listOf("PENDING", "PENDING", "COMPLETED"))
    private var failureReason: String? = null

    private val client = HttpClient(
        MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            when {
                path == "/api/v1/pdf-exports" && request.method == HttpMethod.Post ->
                    json("""{"id":"x1","status":"PENDING","requestedAt":"2026-10-23T15:00:00Z"}""", HttpStatusCode.Accepted)
                path == "/api/v1/pdf-exports/x1" -> {
                    val status = if (statuses.size > 1) statuses.removeFirst() else statuses.first()
                    val reason = failureReason?.let { "\"$it\"" } ?: "null"
                    json("""{"id":"x1","type":"SINGLE_PROGRESS_NOTE","status":"$status","filename":"evolucion-HC-00142-20261023.pdf","failureReason":$reason}""")
                }
                path == "/api/v1/pdf-exports/x1/content" ->
                    respond(PDF_BYTES, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/pdf"))
                else -> error("unexpected request $path")
            }
        },
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        installApiErrorValidator(onUnauthorized = {})
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private val repository = KtorPdfExportRepository(client, VersionedNotes(), PdfExportPolling(initialMillis = 500, maxMillis = 3_000, timeoutMillis = 10_000))

    private fun requestBody() = Json.parseToJsonElement(
        ((requests.first().body as OutgoingContent.ByteArrayContent).bytes()).decodeToString(),
    ).jsonObject

    @Test
    fun `requests the signed evolucion, polls with a growing pause until it's generated, and downloads it`() = runTest {
        val file = repository.export(PdfExportTarget.Evolucion("h1", "e1"))

        assertEquals("evolucion-HC-00142-20261023.pdf", file.filename)
        assertEquals(PDF_BYTES.toList(), file.bytes.toList())
        val body = requestBody()
        assertEquals("SINGLE_PROGRESS_NOTE", body["type"]?.jsonPrimitive?.content)
        assertEquals("h1", body["episodeId"]?.jsonPrimitive?.content)
        assertEquals("e1-version", body["documentVersionId"]?.jsonPrimitive?.content)
        assertEquals(3, requests.count { it.url.encodedPath == "/api/v1/pdf-exports/x1" })
        assertEquals(500L + 750L, currentTime, "two pauses before the third check: 500 ms, then 750 ms")
    }

    @Test
    fun `the historia clinica exports its own version as an admission history`() = runTest {
        repository.export(PdfExportTarget.HistoriaClinica("h1"))

        assertEquals("ADMISSION_HISTORY", requestBody()["type"]?.jsonPrimitive?.content)
        assertEquals("hc-version", requestBody()["documentVersionId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the full record names no version`() = runTest {
        repository.export(PdfExportTarget.FullRecord("h1"))

        assertEquals("FULL_RECORD", requestBody()["type"]?.jsonPrimitive?.content)
        val version = requestBody()["documentVersionId"]
        assertNull(version?.takeUnless { it == JsonNull })
    }

    @Test
    fun `a failed generation reports the backend's reason and downloads nothing`() = runTest {
        statuses.clear()
        statuses += "FAILED"
        failureReason = "El episodio no tiene documentos firmados"

        val failure = assertFailsWith<PdfExportFailedException> { repository.export(PdfExportTarget.FullRecord("h1")) }

        assertEquals("El episodio no tiene documentos firmados", failure.reason)
        assertEquals(0, requests.count { it.url.encodedPath.endsWith("/content") })
    }

    @Test
    fun `an export still pending after the whole wait times out`() = runTest {
        statuses.clear()
        statuses += "PENDING"

        assertFailsWith<PdfExportTimeoutException> { repository.export(PdfExportTarget.FullRecord("h1")) }
        assertEquals(0, requests.count { it.url.encodedPath.endsWith("/content") })
    }
}
