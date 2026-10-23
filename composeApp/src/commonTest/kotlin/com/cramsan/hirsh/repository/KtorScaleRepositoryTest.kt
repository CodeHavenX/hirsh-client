package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.ScaleOption
import com.cramsan.hirsh.model.ScaleScore
import com.cramsan.hirsh.model.ScaleSeverity
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private const val HOSP_ID = "3c9d2e1f-7a4b-4c5d-9e8f-1a2b3c4d5e6f"
private const val DOCUMENT_ID = "8f2a9c41-0b6d-4e3a-9c57-2d1e6f4a8b90"
private const val PHQ9_ID = "456222a0-4d10-41b1-8262-d8972cc3aef4"

private val options = """[{"value":0,"label":"Ningún día"},{"value":1,"label":"Varios días"},{"value":2,"label":"Más de la mitad de los días"},{"value":3,"label":"Casi todos los días"}]"""

private val phq9Json = """
    {"id":"$PHQ9_ID","code":"PHQ-9","version":1,"name":"Cuestionario de salud del paciente (PHQ-9)",
     "jsonStructure":{"stem":"Durante las últimas 2 semanas...","questions":[
       {"id":"q1","text":"Poco interés o placer en hacer cosas","options":$options},
       {"id":"q9","text":"Pensamientos de que estaría mejor muerto(a)","options":$options}]},
     "calculationFormula":"q1 + q9","instructions":"No sugerir respuestas.","requiresTraining":false,"active":true}
""".trimIndent()

private fun appliedJson(id: String, appliedAt: String, total: String, alert: Boolean) = """
    {"id":"$id","clinicalDocumentId":"$DOCUMENT_ID","episodeId":"$HOSP_ID","patientId":"p1",
     "testTemplate":{"id":"$PHQ9_ID","code":"PHQ-9","version":1,"name":"PHQ-9"},
     "appliedAt":"$appliedAt","appliedBy":{"id":"u1","fullName":"Dra. Rosa Quispe"},
     "answersJson":{"q1":3},"totalScore":$total,"severity":"MODERATE","interpretation":"Depresión moderada",
     "suicideAlertTriggered":$alert,"calculationRulesSnapshot":{},"clinicalObservations":"Colaboradora"}
""".trimIndent()

private fun page(vararg items: String) =
    """{"content":[${items.joinToString(",")}],"page":{"number":0,"size":100,"totalElements":${items.size},"totalPages":1}}"""

class KtorScaleRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val appliedOnServer = mutableListOf(appliedJson("a1", "2026-06-20T10:00:00-05:00", "18.00", alert = false))

    private val client = HttpClient(
        MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            val body = when {
                path == "/api/v1/test-templates" -> page(
                    """{"id":"$PHQ9_ID","code":"PHQ-9","version":1,"name":"PHQ-9","description":"Depresión","active":true}""",
                    """{"id":"old","code":"GAD-7","version":1,"name":"GAD-7","active":false}""",
                )
                path == "/api/v1/test-templates/$PHQ9_ID" -> phq9Json
                path.endsWith("/score-preview") ->
                    """{"totalScore":14.00,"sectionScores":{},"severity":"MODERATE","interpretation":"Depresión moderada","wouldTriggerAlerts":["SUICIDE_RISK"]}"""
                path == "/api/v1/clinical-documents/$DOCUMENT_ID/applied-tests" ->
                    appliedJson("a2", "2026-06-21T10:00:00-05:00", "14.00", alert = true).also { appliedOnServer += it }
                path == "/api/v1/episodes/$HOSP_ID/applied-tests" -> page(*appliedOnServer.toTypedArray())
                else -> error("unexpected request $path")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        installApiErrorValidator(onUnauthorized = {})
    }

    private val repository = KtorScaleRepository(client)

    private fun HttpRequestData.jsonBody() =
        Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    @Test
    fun `lists only active scales`() = runTest {
        assertEquals(listOf("PHQ-9"), repository.getScales().map { it.code })
    }

    @Test
    fun `reads a scale's items, options and stem from its structure`() = runTest {
        val scale = repository.getScale(PHQ9_ID)

        assertEquals("Durante las últimas 2 semanas...", scale.stem)
        assertEquals("No sugerir respuestas.", scale.instructions)
        assertEquals(listOf("q1", "q9"), scale.questions.map { it.id })
        assertEquals("Poco interés o placer en hacer cosas", scale.questions.first().text)
        assertEquals(ScaleOption(2, "Más de la mitad de los días"), scale.questions.first().options[2])
    }

    @Test
    fun `previews a score and reads the suicide alert it would raise`() = runTest {
        val score = repository.previewScore(PHQ9_ID, mapOf("q1" to 3, "q9" to 1))

        assertEquals(ScaleScore(14.0, ScaleSeverity.MODERATE, "Depresión moderada", raisesSuicideAlert = true), score)
        val sent = requests.single()
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals(1, sent.jsonBody()["answersJson"]!!.jsonObject["q9"]!!.jsonPrimitive.int)
    }

    @Test
    fun `applying a scale sends it on the document and adds it to the stay's list`() = runTest {
        repository.getAppliedScales(HOSP_ID).test {
            assertEquals(listOf("a1"), awaitItem().map { it.id })

            val applied = repository.applyScale(HOSP_ID, DOCUMENT_ID, PHQ9_ID, mapOf("q1" to 3, "q9" to 1), "  ")

            assertEquals(true, applied.score.raisesSuicideAlert)
            assertEquals(DOCUMENT_ID, applied.documentId)
            assertEquals(Instant.parse("2026-06-21T15:00:00Z"), applied.appliedAt)
            assertEquals("Dra. Rosa Quispe", applied.appliedBy)
            assertEquals(listOf("a2", "a1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        val body = requests.single { it.method == HttpMethod.Post }.jsonBody()
        assertEquals(PHQ9_ID, body["testTemplateId"]!!.jsonPrimitive.content)
        assertNull(body["clinicalObservations"]?.takeIf { it.toString() != "null" }, "a blank note isn't sent")
    }
}
