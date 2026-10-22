package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DischargeCondition
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DischargeType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val PATIENT_ID = "8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10"
private const val OTHER_PATIENT_ID = "a21f8bfa-299c-42db-9681-c84f87a90ce4"
private const val EPISODE_ID = "3c9d2e1f-7a4b-4c5d-9e8f-1a2b3c4d5e6f"

/** One sent request -- method, path, query and body -- for assertions without a real server. */
private data class SentRequest(val method: HttpMethod, val path: String, val query: Map<String, String>, val body: String)

/** Same plugin stack as `KtorEpisodeRepository` runs behind; mirrors [KtorPatientRepositoryTest]'s `mockClient`. */
private fun episodeClient(
    sent: MutableList<SentRequest> = mutableListOf(),
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient {
    val engine = MockEngine { request ->
        val bodyText = when (val content = request.body) {
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            else -> ""
        }
        val query = request.url.parameters.names().associateWith { request.url.parameters[it].orEmpty() }
        sent += SentRequest(request.method, request.url.encodedPath, query, bodyText)
        handler(request)
    }
    return HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        installApiErrorValidator(onUnauthorized = {})
    }
}

private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

private const val PATIENT_HEADER_JSON =
    """{"id": "$PATIENT_ID", "medicalRecordNumber": "HC-2026-004312", "fullName": "Luis Ramos Vega", "age": {"years": 35}}"""

private const val REFS_JSON = """
    "attendingPhysician": {"id": "u1", "fullName": "Dra. Rosa Quispe"},
    "currentService": {"id": "s1", "code": "PSQ", "name": "Psiquiatria"},
    "currentBed": {"id": "b1", "number": "12"}
"""

private fun episodeResponseJson(
    id: String = EPISODE_ID,
    patientId: String = PATIENT_ID,
    status: String = "ACTIVE",
    endDate: String? = null,
    jpaVersion: Long = 4,
    dischargeJson: String = "",
): String = """
    {
      "id": "$id",
      "patient": {"id": "$patientId", "medicalRecordNumber": "HC-2026-004312", "fullName": "Luis Ramos Vega"},
      "episodeType": "INPATIENT",
      "status": "$status",
      "startDate": "2026-06-01T10:00:00-05:00",
      "endDate": ${endDate?.let { "\"$it\"" }},
      "admissionReason": "Ideacion suicida",
      $REFS_JSON,
      $dischargeJson
      "audit": {"createdAt": "2026-06-01T15:00:00Z", "jpaVersion": $jpaVersion}
    }
""".trimIndent()

private fun episodeSummaryJson(id: String, status: String = "CLOSED"): String = """
    {
      "id": "$id",
      "patient": $PATIENT_HEADER_JSON,
      "episodeType": "INPATIENT",
      "status": "$status",
      "startDate": "2026-06-01T10:00:00-05:00",
      "admissionReason": "Ideacion suicida",
      $REFS_JSON
    }
""".trimIndent()

private fun pageJson(content: List<String>, number: Int, totalPages: Int): String =
    """{"content": [${content.joinToString(",")}], "page": {"number": $number, "size": 100, "totalElements": 0, "totalPages": $totalPages}}"""

class KtorEpisodeRepositoryTest {

    // --- getHospitalizations -------------------------------------------------------------

    @Test
    fun `getHospitalizations asks for INPATIENT episodes only and maps each summary`() = runTest {
        val sent = mutableListOf<SentRequest>()
        val client = episodeClient(sent) { json(HttpStatusCode.OK, pageJson(listOf(episodeSummaryJson("e1", "ACTIVE")), 0, 1)) }
        val repository = KtorEpisodeRepository(client)

        val list = repository.getHospitalizations(PATIENT_ID).first()

        val request = sent.single()
        assertEquals("/api/v1/patients/$PATIENT_ID/episodes", request.path)
        assertEquals("INPATIENT", request.query["episodeType"])
        val episode = list.single()
        assertEquals("e1", episode.id)
        assertEquals(PATIENT_ID, episode.patientId)
        assertEquals(EpisodeStatus.ACTIVE, episode.status)
        assertEquals(Instant.parse("2026-06-01T15:00:00Z"), episode.startDate, "the wire offset must be honoured")
        assertNull(episode.endDate)
        assertEquals(ServiceRef("s1", "Psiquiatria"), episode.currentService)
        assertEquals(BedRef("b1", "12"), episode.currentBed)
        assertEquals(UserRef("u1", "Dra. Rosa Quispe"), episode.attendingPhysician)
        assertEquals("Ideacion suicida", episode.admissionReason)
    }

    @Test
    fun `getHospitalizations merges every page, in order`() = runTest {
        val client = episodeClient { request ->
            when (request.url.parameters["page"]?.toInt() ?: 0) {
                0 -> json(HttpStatusCode.OK, pageJson(listOf(episodeSummaryJson("e0")), 0, 2))
                else -> json(HttpStatusCode.OK, pageJson(listOf(episodeSummaryJson("e1")), 1, 2))
            }
        }
        val repository = KtorEpisodeRepository(client)

        assertEquals(listOf("e0", "e1"), repository.getHospitalizations(PATIENT_ID).first().map { it.id })
    }

    @Test
    fun `getHospitalizations propagates a failed fetch instead of reporting no stays`() = runTest {
        val client = episodeClient { json(HttpStatusCode.InternalServerError, """{"title": "Internal Server Error"}""") }
        val repository = KtorEpisodeRepository(client)

        val error = assertFailsWith<ApiException> { repository.getHospitalizations(PATIENT_ID).first() }
        assertTrue(error.error is ApiError.Unknown)
    }

    @Test
    fun `getHospitalizations reports no stays for an unknown patient`() = runTest {
        val client = episodeClient { json(HttpStatusCode.NotFound, """{"title": "Not Found"}""") }
        val repository = KtorEpisodeRepository(client)

        assertEquals(emptyList(), repository.getHospitalizations(PATIENT_ID).first())
    }

    // --- getHospitalization --------------------------------------------------------------

    @Test
    fun `getHospitalization fetches by episode id and maps the full response`() = runTest {
        val sent = mutableListOf<SentRequest>()
        val client = episodeClient(sent) {
            json(HttpStatusCode.OK, episodeResponseJson(status = "CLOSED", endDate = "2026-06-10T09:30:00Z", jpaVersion = 9))
        }
        val repository = KtorEpisodeRepository(client)

        val episode = repository.getHospitalization(PATIENT_ID, EPISODE_ID).first()

        assertEquals("/api/v1/episodes/$EPISODE_ID", sent.single().path)
        assertEquals(EPISODE_ID, episode?.id)
        assertEquals(EpisodeStatus.CLOSED, episode?.status)
        assertEquals(Instant.parse("2026-06-10T09:30:00Z"), episode?.endDate)
        assertEquals("Ideacion suicida", episode?.admissionReason)
        assertEquals(9L, episode?.jpaVersion)
    }

    @Test
    fun `getHospitalization of another patient's episode is not found`() = runTest {
        val client = episodeClient { json(HttpStatusCode.OK, episodeResponseJson(patientId = OTHER_PATIENT_ID)) }
        val repository = KtorEpisodeRepository(client)

        assertNull(repository.getHospitalization(PATIENT_ID, EPISODE_ID).first())
    }

    @Test
    fun `getHospitalization returns null on a 404 instead of throwing`() = runTest {
        val client = episodeClient { json(HttpStatusCode.NotFound, """{"title": "Not Found"}""") }
        val repository = KtorEpisodeRepository(client)

        assertNull(repository.getHospitalization(PATIENT_ID, EPISODE_ID).first())
    }

    @Test
    fun `getHospitalization still throws on errors other than 404`() = runTest {
        val client = episodeClient { json(HttpStatusCode.Forbidden, """{"title": "Forbidden"}""") }
        val repository = KtorEpisodeRepository(client)

        val error = assertFailsWith<ApiException> { repository.getHospitalization(PATIENT_ID, EPISODE_ID).first() }
        assertEquals(ApiError.Forbidden, error.error)
    }

    // --- addHospitalization ---------------------------------------------------------------

    @Test
    fun `addHospitalization posts an INPATIENT CreateEpisodeRequest with the refs' ids`() = runTest {
        val sent = mutableListOf<SentRequest>()
        val client = episodeClient(sent) { json(HttpStatusCode.Created, episodeResponseJson()) }
        val repository = KtorEpisodeRepository(client)

        repository.addHospitalization(
            patientId = PATIENT_ID,
            service = ServiceRef("s1", "Psiquiatria"),
            bed = BedRef("b1", "12"),
            attendingPhysician = UserRef("u1", "Dra. Rosa Quispe"),
            admissionReason = "Ideacion suicida",
        )

        val request = sent.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/episodes", request.path)
        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals(PATIENT_ID, body["patientId"]?.jsonPrimitive?.content)
        assertEquals("INPATIENT", body["episodeType"]?.jsonPrimitive?.content)
        assertEquals("u1", body["attendingPhysicianId"]?.jsonPrimitive?.content)
        assertEquals("s1", body["serviceId"]?.jsonPrimitive?.content)
        assertEquals("b1", body["bedId"]?.jsonPrimitive?.content)
        assertEquals("Ideacion suicida", body["admissionReason"]?.jsonPrimitive?.content)
        assertTrue("startDate" !in body, "the server stamps the start date")
    }

    @Test
    fun `addHospitalization surfaces the backend's 409 for an already-open stay as Conflict`() = runTest {
        val client = episodeClient { json(HttpStatusCode.Conflict, """{"conflictingResourceId": "open-episode"}""") }
        val repository = KtorEpisodeRepository(client)

        val error = assertFailsWith<ApiException> {
            repository.addHospitalization(PATIENT_ID, ServiceRef("s1", "Psiquiatria"), null, UserRef("u1", "Dra. Rosa Quispe"), null)
        }
        assertEquals(ApiError.Conflict("open-episode"), error.error)
    }

    @Test
    fun `addHospitalization reaches an already-collected list without a re-fetch`() = runTest {
        val sent = mutableListOf<SentRequest>()
        val client = episodeClient(sent) { request ->
            if (request.method == HttpMethod.Post) {
                json(HttpStatusCode.Created, episodeResponseJson())
            } else {
                json(HttpStatusCode.OK, pageJson(listOf(episodeSummaryJson("older")), 0, 1))
            }
        }
        val repository = KtorEpisodeRepository(client)

        repository.getHospitalizations(PATIENT_ID).test {
            assertEquals(listOf("older"), awaitItem().map { it.id })

            repository.addHospitalization(PATIENT_ID, ServiceRef("s1", "Psiquiatria"), null, UserRef("u1", "Dra. Rosa Quispe"), null)

            assertEquals(listOf(EPISODE_ID, "older"), awaitItem().map { it.id }, "newest first, like the real list")
        }
        assertEquals(1, sent.count { it.method == HttpMethod.Get }, "the list is not re-fetched")
    }

    // --- discharge ------------------------------------------------------------------------

    @Test
    fun `discharge posts every field, the next appointment as ISO, and the version`() = runTest {
        val sent = mutableListOf<SentRequest>()
        val client = episodeClient(sent) { json(HttpStatusCode.OK, episodeResponseJson(status = "CLOSED", endDate = "2026-06-10T09:30:00Z")) }
        val repository = KtorEpisodeRepository(client)

        repository.discharge(
            EPISODE_ID,
            DischargeDetails(
                type = DischargeType.MEDICAL,
                condition = DischargeCondition.IMPROVED,
                diagnosisIcd10 = "F32.2",
                followUpPlan = "Control en 15 dias",
                nextAppointmentDate = "17/07/2026",
            ),
            jpaVersion = 4,
        )

        val request = sent.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/episodes/$EPISODE_ID/discharge", request.path)
        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals("MEDICAL", body["dischargeType"]?.jsonPrimitive?.content)
        assertEquals("IMPROVED", body["dischargeCondition"]?.jsonPrimitive?.content)
        assertEquals("F32.2", body["dischargeDiagnosisIcd10"]?.jsonPrimitive?.content)
        assertEquals("Control en 15 dias", body["followUpPlan"]?.jsonPrimitive?.content)
        assertEquals("2026-07-17", body["nextAppointmentDate"]?.jsonPrimitive?.content)
        assertEquals("4", body["jpaVersion"]?.jsonPrimitive?.content)
        assertTrue("endDate" !in body, "the server stamps the end date")
    }

    @Test
    fun `discharge reaches an open stay and its list row without a re-fetch`() = runTest {
        val discharged = episodeResponseJson(
            status = "TRANSFERRED",
            endDate = "2026-06-10T09:30:00Z",
            jpaVersion = 5,
            dischargeJson = """"dischargeType": "TRANSFER", "dischargeCondition": "UNCHANGED",
                "dischargeDiagnosisIcd10": "F20.0", "nextAppointmentDate": "2026-07-17",""",
        )
        val sent = mutableListOf<SentRequest>()
        val client = episodeClient(sent) { request ->
            when {
                request.method == HttpMethod.Post -> json(HttpStatusCode.OK, discharged)
                request.url.encodedPath.startsWith("/api/v1/episodes/") -> json(HttpStatusCode.OK, episodeResponseJson())
                else -> json(HttpStatusCode.OK, pageJson(listOf(episodeSummaryJson(EPISODE_ID, "ACTIVE")), 0, 1))
            }
        }
        val repository = KtorEpisodeRepository(client)

        repository.getHospitalizations(PATIENT_ID).test {
            assertEquals(EpisodeStatus.ACTIVE, awaitItem().single().status)
            repository.getHospitalization(PATIENT_ID, EPISODE_ID).test {
                assertEquals(EpisodeStatus.ACTIVE, awaitItem()?.status)

                repository.discharge(EPISODE_ID, DischargeDetails(DischargeType.TRANSFER, DischargeCondition.UNCHANGED, "F20.0"), 4)

                val stay = awaitItem()
                assertEquals(EpisodeStatus.TRANSFERRED, stay?.status)
                assertEquals(5L, stay?.jpaVersion)
                assertEquals(
                    DischargeDetails(DischargeType.TRANSFER, DischargeCondition.UNCHANGED, "F20.0", nextAppointmentDate = "17/07/2026"),
                    stay?.discharge,
                )
            }
            val row = awaitItem().single()
            assertEquals(EpisodeStatus.TRANSFERRED, row.status)
            assertEquals(Instant.parse("2026-06-10T09:30:00Z"), row.endDate)
        }
        assertEquals(2, sent.count { it.method == HttpMethod.Get }, "one list fetch and one stay fetch, no re-fetch")
    }

    @Test
    fun `discharge surfaces the backend's 409 as Conflict`() = runTest {
        val client = episodeClient { json(HttpStatusCode.Conflict, """{"detail": "Episode was modified by another user."}""") }
        val repository = KtorEpisodeRepository(client)

        val error = assertFailsWith<ApiException> {
            repository.discharge(EPISODE_ID, DischargeDetails(DischargeType.MEDICAL, DischargeCondition.IMPROVED, "F32.2"), 1)
        }
        assertEquals(ApiError.Conflict(""), error.error)
    }
}
