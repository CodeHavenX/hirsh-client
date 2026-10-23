package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.RouteOfAdministration
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PATIENT_ID = "07c98942-3654-4034-b960-f3265814e214"

private fun treatmentJson(id: String, drug: String) = """
    {"id":"$id","drugName":"$drug","dose":"50 mg","frequency":"Cada 24 h","routeOfAdministration":"PO",
     "episodeId":"h1","episodeStartDate":"2026-06-15"}
""".trimIndent()

/** Only [signatures] matters to the treatment list. */
private class SigningNotes : ClinicalNoteRepository {
    override val signatures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override fun getHistoriaClinica(hospId: String): Flow<HistoriaClinica> = emptyFlow()
    override fun getEvoluciones(hospId: String): Flow<List<Evolucion>> = emptyFlow()
    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> = flowOf(null)
    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) = Unit
    override suspend fun signHistoriaClinica(hospId: String) = Unit
    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = evolucion
    override suspend fun signEvolucion(hospId: String, evoId: String) = Unit
}

class KtorTreatmentRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val onServer = mutableListOf(treatmentJson("t1", "Sertralina"))
    private val notes = SigningNotes()

    private val client = HttpClient(
        MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            val body = when {
                path == "/api/v1/patients/$PATIENT_ID/treatments/active" -> "[${onServer.joinToString(",")}]"
                path == "/api/v1/prescribed-treatments/t1/suspension" -> treatmentJson("t1", "Sertralina").also { onServer.clear() }
                else -> error("unexpected request $path")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        installApiErrorValidator(onUnauthorized = {})
    }

    private val repository = KtorTreatmentRepository(client, notes)

    @Test
    fun `reads the patient's active treatments`() = runTest {
        repository.getActiveTreatments(PATIENT_ID).test {
            val treatment = awaitItem().single()
            assertEquals("Sertralina", treatment.drugName)
            assertEquals(RouteOfAdministration.PO, treatment.route)
            assertEquals(LocalDate(2026, 6, 15), treatment.episodeStartDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a signed note re-reads the list, since signing is what creates treatments`() = runTest {
        repository.getActiveTreatments(PATIENT_ID).test {
            assertEquals(listOf("t1"), awaitItem().map { it.id })

            onServer += treatmentJson("t2", "Quetiapina")
            notes.signatures.subscriptionCount.first { it > 0 }
            notes.signatures.emit(Unit)

            assertEquals(listOf("t1", "t2"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `suspending sends the reason and last day, then re-reads the list`() = runTest {
        repository.getActiveTreatments(PATIENT_ID).test {
            assertEquals(1, awaitItem().size)

            repository.suspend("t1", "Nauseas persistentes", LocalDate(2026, 6, 20))

            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        val put = requests.single { it.method == HttpMethod.Put }
        val body = Json.parseToJsonElement((put.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
        assertEquals("Nauseas persistentes", body["suspensionReason"]!!.jsonPrimitive.content)
        assertEquals("2026-06-20", body["endDate"]!!.jsonPrimitive.content)
    }
}
