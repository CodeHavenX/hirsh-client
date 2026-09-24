package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PATIENT_ID = "8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10"

private const val ALLERGY_A1_JSON =
    """[{"id":"a1","allergyType":"MEDICATION","description":"Penicilina","severity":"SEVERE"}]"""

/** One recorded request, for assertions on the outgoing method/path/body without a real server. */
private data class RecordedRequest(val method: HttpMethod, val path: String, val body: String)

/**
 * [handler] decides the response for each request; every request is also appended to [recorded]
 * so a test can assert on what was actually sent (e.g. that `updatePatient` never sends an
 * immutable field). Mirrors [KtorAuthRepositoryTest]'s `clientRespondingWith` -- wrapped in the
 * same plugin stack `KtorPatientRepository` actually runs behind.
 */
private fun mockClient(
    recorded: MutableList<RecordedRequest> = mutableListOf(),
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient {
    val engine = MockEngine { request ->
        val bodyText = when (val content = request.body) {
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            else -> ""
        }
        recorded += RecordedRequest(request.method, request.url.encodedPath, bodyText)
        handler(request)
    }
    return HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        installApiErrorValidator(onUnauthorized = {})
    }
}

private fun MockRequestHandleScope.jsonResponse(status: HttpStatusCode, body: String): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

private suspend fun <T> flowFirst(flow: Flow<T>): T = flow.first()

private fun patientResponseJson(
    id: String = "8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10",
    medicalRecordNumber: String = "HC-2026-004312",
    documentType: String = "NID",
    documentNumber: String = "45821337",
    firstName: String = "Luis",
    lastName: String = "Ramos",
    secondLastName: String? = "Vega",
    fullName: String = "Luis Ramos Vega",
    birthDate: String = "1991-04-12",
    sex: String = "M",
    bloodType: String? = "O+",
    phone: String? = null,
    allergiesJson: String = "[]",
    jpaVersion: Long = 3,
): String = """
    {
      "id": "$id",
      "medicalRecordNumber": "$medicalRecordNumber",
      "documentType": "$documentType",
      "documentNumber": "$documentNumber",
      "firstName": "$firstName",
      "lastName": "$lastName",
      "secondLastName": ${secondLastName?.let { "\"$it\"" }},
      "fullName": "$fullName",
      "birthDate": "$birthDate",
      "sex": "$sex",
      "bloodType": ${bloodType?.let { "\"$it\"" }},
      "phone": ${phone?.let { "\"$it\"" }},
      "allergies": $allergiesJson,
      "audit": {
        "createdAt": "2026-06-26T13:30:00Z",
        "jpaVersion": $jpaVersion
      }
    }
""".trimIndent()

private fun pageJson(content: List<String>, number: Int, totalPages: Int): String =
    """{"content": [${content.joinToString(",")}], "page": {"number": $number, "size": 100, "totalElements": 0, "totalPages": $totalPages}}"""

class KtorPatientRepositoryTest {

    // --- patients (fetch-all-pages) ------------------------------------------------------

    @Test
    fun `refresh issues exactly one request when the first page is the only page`() = runTest {
        val recorded = mutableListOf<RecordedRequest>()
        val client = mockClient(recorded) { jsonResponse(HttpStatusCode.OK, pageJson(listOf(patientResponseJson()), 0, 1)) }
        val repository = KtorPatientRepository(client)

        repository.refresh()

        assertEquals(1, repository.patients.value.size)
        assertEquals(1, recorded.count { it.path == "/api/v1/patients" })
    }

    @Test
    fun `refresh merges every page into one flat list, in order`() = runTest {
        var callCount = 0
        val client = mockClient { request ->
            val page = request.url.parameters["page"]?.toInt() ?: 0
            callCount++
            when (page) {
                0 -> jsonResponse(HttpStatusCode.OK, pageJson(listOf(patientResponseJson(id = "p0")), 0, 3))
                1 -> jsonResponse(HttpStatusCode.OK, pageJson(listOf(patientResponseJson(id = "p1")), 1, 3))
                else -> jsonResponse(HttpStatusCode.OK, pageJson(listOf(patientResponseJson(id = "p2")), 2, 3))
            }
        }
        val repository = KtorPatientRepository(client)

        repository.refresh()

        assertEquals(3, callCount)
        assertEquals(listOf("p0", "p1", "p2"), repository.patients.value.map { it.id })
    }

    @Test
    fun `refresh leaves patients empty when the server has no patients`() = runTest {
        val client = mockClient { jsonResponse(HttpStatusCode.OK, pageJson(emptyList(), 0, 1)) }
        val repository = KtorPatientRepository(client)

        repository.refresh()

        assertEquals(emptyList(), repository.patients.value)
    }

    // --- getPatient ------------------------------------------------------------------------

    @Test
    fun `getPatient maps every PatientResponse field, including sex, allergies and jpaVersion`() = runTest {
        val client = mockClient {
            jsonResponse(
                HttpStatusCode.OK,
                patientResponseJson(
                    sex = "F",
                    allergiesJson = """
                        [{"id":"a1","allergyType":"MEDICATION","description":"Penicilina","severity":"SEVERE","observations":"Urticaria"}]
                    """.trimIndent(),
                    jpaVersion = 7,
                ),
            )
        }
        val repository = KtorPatientRepository(client)

        val patient = flowFirst(repository.getPatient("8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10"))

        assertEquals("8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10", patient?.id)
        assertEquals("HC-2026-004312", patient?.medicalRecordNumber)
        assertEquals(DocumentType.NID, patient?.documentType)
        assertEquals("Luis", patient?.firstName)
        assertEquals("Ramos", patient?.lastName)
        assertEquals("Vega", patient?.secondLastName)
        assertEquals("Luis Ramos Vega", patient?.fullName)
        assertEquals("12/04/1991", patient?.birthDate, "wire ISO date must convert to this client's dd/MM/yyyy")
        assertEquals(Sex.FEMALE, patient?.sex)
        assertEquals("O+", patient?.bloodType)
        assertEquals(7L, patient?.jpaVersion)
        assertEquals(1, patient?.allergies?.size)
        val allergy = patient?.allergies?.single()
        assertEquals(AllergyType.MEDICATION, allergy?.allergyType)
        assertEquals("Penicilina", allergy?.description)
        assertEquals(Severity.SEVERE, allergy?.severity)
        assertEquals("Urticaria", allergy?.observations)
    }

    @Test
    fun `getPatient returns null on a 404 instead of throwing`() = runTest {
        val client = mockClient { jsonResponse(HttpStatusCode.NotFound, """{"title":"Not Found"}""") }
        val repository = KtorPatientRepository(client)

        assertNull(flowFirst(repository.getPatient("does-not-exist")))
    }

    @Test
    fun `getPatient rethrows a non-404 error rather than swallowing it into null`() = runTest {
        val client = mockClient { jsonResponse(HttpStatusCode.InternalServerError, "not json") }
        val repository = KtorPatientRepository(client)

        assertFailsWith<ApiException> { flowFirst(repository.getPatient("any-id")) }
    }

    // --- updatePatient -----------------------------------------------------------------------

    @Test
    fun `updatePatient sends only PatchPatientRequest's fields, never an immutable identity field`() = runTest {
        val recorded = mutableListOf<RecordedRequest>()
        val client = mockClient(recorded) { request ->
            if (request.method == HttpMethod.Patch) {
                jsonResponse(HttpStatusCode.OK, patientResponseJson(phone = "999-999-999"))
            } else {
                jsonResponse(HttpStatusCode.OK, patientResponseJson())
            }
        }
        val repository = KtorPatientRepository(client)
        val original = flowFirst(repository.getPatient("8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10"))!!

        repository.updatePatient(
            id = original.id,
            newValues = original.copy(phone = "999-999-999"),
            changedBy = "unused",
            fecha = "unused",
            hora = "unused",
        )

        val patchRequest = recorded.single { it.method == HttpMethod.Patch }
        assertEquals("/api/v1/patients/${original.id}", patchRequest.path)
        assertTrue("999-999-999" in patchRequest.body)
        assertTrue("documentNumber" !in patchRequest.body)
        assertTrue("birthDate" !in patchRequest.body)
        assertTrue("\"sex\"" !in patchRequest.body)
    }

    @Test
    fun `updatePatient replaces the cached patient with the server's response`() = runTest {
        val client = mockClient { request ->
            if (request.method == HttpMethod.Patch) {
                jsonResponse(HttpStatusCode.OK, patientResponseJson(phone = "999-999-999"))
            } else {
                jsonResponse(HttpStatusCode.OK, pageJson(listOf(patientResponseJson()), 0, 1))
            }
        }
        val repository = KtorPatientRepository(client)
        repository.refresh()

        repository.updatePatient(
            id = "8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10",
            newValues = repository.patients.value.single().copy(phone = "999-999-999"),
            changedBy = "unused",
            fecha = "unused",
            hora = "unused",
        )

        assertEquals("999-999-999", repository.patients.value.single().phone)
    }

    @Test
    fun `updatePatient propagates a 409 as ApiException`() = runTest {
        val client = mockClient { request ->
            if (request.method == HttpMethod.Patch) {
                jsonResponse(HttpStatusCode.Conflict, """{"conflictingResourceId": "8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10"}""")
            } else {
                jsonResponse(HttpStatusCode.OK, patientResponseJson())
            }
        }
        val repository = KtorPatientRepository(client)
        val original = flowFirst(repository.getPatient("8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10"))!!

        assertFailsWith<ApiException> {
            repository.updatePatient(
                id = original.id,
                newValues = original.copy(phone = "999-999-999"),
                changedBy = "unused",
                fecha = "unused",
                hora = "unused",
            )
        }
    }

    // --- addPatient --------------------------------------------------------------------------

    @Test
    fun `addPatient sends CreatePatientRequest's exact shape and maps the 201 response`() = runTest {
        val recorded = mutableListOf<RecordedRequest>()
        val client = mockClient(recorded) { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath == "/api/v1/patients" ->
                    jsonResponse(HttpStatusCode.Created, patientResponseJson(phone = "555-0100"))
                else -> jsonResponse(HttpStatusCode.NotFound, "{}")
            }
        }
        val repository = KtorPatientRepository(client)

        val created = repository.addPatient(
            medicalRecordNumber = "HC-2026-004312",
            firstName = "Luis",
            lastName = "Ramos",
            secondLastName = "Vega",
            documentType = DocumentType.NID,
            documentNumber = "45821337",
            birthDate = "12/04/1991",
            phone = "555-0100",
            sex = Sex.MALE,
            bloodType = "",
        )

        val createRequest = recorded.single { it.method == HttpMethod.Post }
        assertTrue("HC-2026-004312" in createRequest.body)
        assertTrue("1991-04-12" in createRequest.body, "birthDate must convert dd/MM/yyyy -> ISO on the wire")
        assertTrue("\"phone\":\"555-0100\"" in createRequest.body, "the phone collected at registration must be sent")
        assertEquals("8f14e45f-9c4b-4d1e-8a2f-6b3c5d7e9a10", created.id)
        assertEquals("555-0100", created.phone)
        assertEquals(1, recorded.size, "addPatient must not make any follow-up call -- allergies are posted separately")
    }

    @Test
    fun `addPatient propagates a 409 duplicate-document conflict as ApiException`() = runTest {
        val client = mockClient { jsonResponse(HttpStatusCode.Conflict, """{"conflictingResourceId": "existing-id"}""") }
        val repository = KtorPatientRepository(client)

        assertFailsWith<ApiException> {
            repository.addPatient(
                medicalRecordNumber = "HC-2026-004312",
                firstName = "Luis",
                lastName = "Ramos",
                secondLastName = "",
                documentType = DocumentType.NID,
                documentNumber = "45821337",
                birthDate = "12/04/1991",
                phone = "",
                sex = Sex.MALE,
                bloodType = "",
            )
        }
    }

    // --- getPatient keeps observing ------------------------------------------------------------

    @Test
    fun `getPatient keeps emitting after its fetch, reflecting a later mutation without a re-fetch`() = runTest {
        val recorded = mutableListOf<RecordedRequest>()
        val client = mockClient(recorded) { request ->
            if (request.method == HttpMethod.Post) {
                jsonResponse(HttpStatusCode.Created, """{"id":"a2","allergyType":"FOOD","description":"Mariscos"}""")
            } else {
                jsonResponse(HttpStatusCode.OK, patientResponseJson(allergiesJson = ALLERGY_A1_JSON))
            }
        }
        val repository = KtorPatientRepository(client)

        repository.getPatient(PATIENT_ID).test {
            assertEquals(listOf("a1"), awaitItem()?.allergies?.map { it.id })

            repository.addAllergy(PATIENT_ID, AllergyType.FOOD, "Mariscos", severity = null, observations = "")

            assertEquals(listOf("a2", "a1"), awaitItem()?.allergies?.map { it.id }, "newest first, like the real list endpoint")
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, recorded.count { it.method == HttpMethod.Get }, "the second emission must come from the cache, not a re-fetch")
    }

    // --- allergies ------------------------------------------------------------------------------

    @Test
    fun `addAllergy posts CreateAllergyRequest to the sub-resource and maps the 201 response`() = runTest {
        val recorded = mutableListOf<RecordedRequest>()
        val client = mockClient(recorded) {
            jsonResponse(
                HttpStatusCode.Created,
                """{"id":"a2","allergyType":"MEDICATION","description":"Ibuprofeno","severity":"MODERATE","observations":"Rash"}""",
            )
        }
        val repository = KtorPatientRepository(client)

        val allergy = repository.addAllergy(PATIENT_ID, AllergyType.MEDICATION, "  Ibuprofeno ", Severity.MODERATE, "Rash")

        val post = recorded.single()
        assertEquals(HttpMethod.Post, post.method)
        assertEquals("/api/v1/patients/$PATIENT_ID/allergies", post.path)
        assertTrue("\"allergyType\":\"MEDICATION\"" in post.body)
        assertTrue("\"description\":\"Ibuprofeno\"" in post.body, "description is trimmed before sending")
        assertTrue("\"severity\":\"MODERATE\"" in post.body)
        assertEquals("a2", allergy.id)
        assertEquals(Severity.MODERATE, allergy.severity)
        assertEquals("Rash", allergy.observations)
    }

    @Test
    fun `addAllergy leaves severity and observations out of the body when ungraded and blank`() = runTest {
        val recorded = mutableListOf<RecordedRequest>()
        val client = mockClient(recorded) {
            jsonResponse(HttpStatusCode.Created, """{"id":"a2","allergyType":"OTHER","description":"Latex"}""")
        }
        val repository = KtorPatientRepository(client)

        val allergy = repository.addAllergy(PATIENT_ID, AllergyType.OTHER, "Latex", severity = null, observations = " ")

        assertTrue("\"severity\":\"" !in recorded.single().body)
        assertTrue("\"observations\":\"" !in recorded.single().body)
        assertNull(allergy.severity, "an ungraded allergy stays ungraded -- never defaulted to MILD")
    }

    @Test
    fun `updateAllergy patches only severity and observations, never the agent itself`() = runTest {
        val recorded = mutableListOf<RecordedRequest>()
        val client = mockClient(recorded) { request ->
            if (request.method == HttpMethod.Patch) {
                jsonResponse(
                    HttpStatusCode.OK,
                    """{"id":"a1","allergyType":"MEDICATION","description":"Penicilina","severity":"MILD","observations":"Revisado"}""",
                )
            } else {
                jsonResponse(HttpStatusCode.OK, patientResponseJson(allergiesJson = ALLERGY_A1_JSON))
            }
        }
        val repository = KtorPatientRepository(client)
        flowFirst(repository.getPatient(PATIENT_ID))

        val updated = repository.updateAllergy(PATIENT_ID, "a1", Severity.MILD, "Revisado")

        val patch = recorded.single { it.method == HttpMethod.Patch }
        assertEquals("/api/v1/patients/$PATIENT_ID/allergies/a1", patch.path)
        assertTrue("\"severity\":\"MILD\"" in patch.body)
        assertTrue("\"observations\":\"Revisado\"" in patch.body)
        assertTrue("description" !in patch.body)
        assertTrue("allergyType" !in patch.body)
        assertEquals(Severity.MILD, updated.severity)
    }

    @Test
    fun `deleteAllergy sends DELETE, accepts a bodyless 204, and drops the allergy from the cache`() = runTest {
        val recorded = mutableListOf<RecordedRequest>()
        val client = mockClient(recorded) { request ->
            if (request.method == HttpMethod.Delete) {
                respond("", HttpStatusCode.NoContent)
            } else {
                jsonResponse(HttpStatusCode.OK, patientResponseJson(allergiesJson = ALLERGY_A1_JSON))
            }
        }
        val repository = KtorPatientRepository(client)

        repository.getPatient(PATIENT_ID).test {
            assertEquals(1, awaitItem()?.allergies?.size)

            repository.deleteAllergy(PATIENT_ID, "a1")

            assertEquals(emptyList(), awaitItem()?.allergies)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("/api/v1/patients/$PATIENT_ID/allergies/a1", recorded.single { it.method == HttpMethod.Delete }.path)
    }

    @Test
    fun `updateAllergy propagates a 404 as ApiException`() = runTest {
        val client = mockClient { jsonResponse(HttpStatusCode.NotFound, """{"title":"Not Found"}""") }
        val repository = KtorPatientRepository(client)

        assertFailsWith<ApiException> { repository.updateAllergy(PATIENT_ID, "gone", Severity.MILD, "") }
    }

    // --- getChangeLog ------------------------------------------------------------------------

    @Test
    fun `getChangeLog is always empty -- no real endpoint exists yet (HISS-624)`() = runTest {
        val repository = KtorPatientRepository(mockClient { jsonResponse(HttpStatusCode.OK, "{}") })

        assertEquals(emptyList(), flowFirst(repository.getChangeLog("any-id")))
    }
}
