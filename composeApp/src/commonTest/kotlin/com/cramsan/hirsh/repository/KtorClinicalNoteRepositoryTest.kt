package com.cramsan.hirsh.repository

import app.cash.turbine.test
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Prescripcion
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.RouteOfAdministration
import com.cramsan.hirsh.model.Vitals
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val HOSP_ID = "3c9d2e1f-7a4b-4c5d-9e8f-1a2b3c4d5e6f"
private const val OTHER_HOSP_ID = "a21f8bfa-299c-42db-9681-c84f87a90ce4"
private const val HC_TEMPLATE_ID = "tpl-hc"
private const val EVOL_TEMPLATE_ID = "tpl-evol"

private val wireJson = Json { ignoreUnknownKeys = true }

/** One sent request, for assertions without a real server. */
private data class DocumentRequest(val method: HttpMethod, val path: String, val query: Map<String, String>, val body: String) {
    val json: JsonObject get() = wireJson.parseToJsonElement(body).jsonObject
}

/** A stored document of [FakeDocumentBackend]. */
private data class StoredDocument(
    val id: String,
    val episodeId: String,
    val category: String,
    var status: String = "DRAFT",
    var content: JsonObject,
    var jpaVersion: Long = 0,
    val documentDate: String = "2026-06-21T16:00:00Z",
    val draftDeadline: String? = null,
    val author: String = "Dra. Rosa Quispe",
)

/**
 * Just enough of the backend's document endpoints to drive the repository: templates, folder,
 * detail, create, autosave with its lock token, and sign. Every request is recorded in [sent].
 */
private class FakeDocumentBackend {
    val documents = mutableListOf<StoredDocument>()
    val sent = mutableListOf<DocumentRequest>()
    private var nextId = 1

    /** Bumps a document's token as if another user had just saved it. */
    fun saveElsewhere(id: String, content: JsonObject) {
        val document = documents.single { it.id == id }
        document.content = content
        document.jpaVersion++
    }

    fun client(): HttpClient {
        val engine = MockEngine { request ->
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            val query = request.url.parameters.names().associateWith { request.url.parameters[it].orEmpty() }
            val sentRequest = DocumentRequest(request.method, request.url.encodedPath, query, body)
            sent += sentRequest
            handle(sentRequest)
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(wireJson) }
            installApiErrorValidator(onUnauthorized = {})
        }
    }

    private fun MockRequestHandleScope.handle(request: DocumentRequest): HttpResponseData {
        val path = request.path
        return when {
            request.method == HttpMethod.Get && path == "/api/v1/document-templates" -> ok(
                page(
                    listOf(
                        """{"id": "$HC_TEMPLATE_ID", "code": "HC_PSIQ", "category": "ADMISSION"}""",
                        """{"id": "$EVOL_TEMPLATE_ID", "code": "EVOL_PSIQ", "category": "PROGRESS_NOTE"}""",
                        """{"id": "tpl-other", "code": "OTHER_FORM", "category": "PROGRESS_NOTE"}""",
                    ).filter { request.query["category"].orEmpty() in it },
                ),
            )
            request.method == HttpMethod.Get && path.matches(Regex("/api/v1/episodes/[^/]+/documents")) -> {
                val episodeId = path.split("/")[4]
                if (episodeId == "unknown") return notFound()
                ok(
                    page(
                        documents.filter { it.episodeId == episodeId && it.category == request.query["category"] }
                            .map { """{"id": "${it.id}", "documentStatus": "${it.status}"}""" },
                    ),
                )
            }
            request.method == HttpMethod.Post && path.matches(Regex("/api/v1/episodes/[^/]+/documents")) -> {
                val episodeId = path.split("/")[4]
                val category = if (request.json["templateId"]!!.jsonPrimitive.content == HC_TEMPLATE_ID) "ADMISSION" else "PROGRESS_NOTE"
                if (category == "ADMISSION" && documents.any { it.episodeId == episodeId && it.category == "ADMISSION" }) {
                    return conflict()
                }
                val document = StoredDocument(
                    id = "doc-${nextId++}",
                    episodeId = episodeId,
                    category = category,
                    content = request.json["initialContent"]!!.jsonObject,
                )
                documents += document
                json(HttpStatusCode.Created, detail(document))
            }
            request.method == HttpMethod.Get && path.startsWith("/api/v1/clinical-documents/") -> {
                val document = documents.find { it.id == path.substringAfterLast("/") } ?: return notFound()
                ok(detail(document))
            }
            request.method == HttpMethod.Put && path.endsWith("/draft") -> {
                val document = documentOfVersion(path)
                if (document.status != "DRAFT" || request.json["jpaVersion"]!!.jsonPrimitive.long != document.jpaVersion) {
                    return conflict()
                }
                document.content = request.json["content"]!!.jsonObject
                document.jpaVersion++
                ok("""{"versionId": "v-${document.id}", "jpaVersion": ${document.jpaVersion}, "savedAt": "2026-06-21T16:05:00Z"}""")
            }
            request.method == HttpMethod.Post && path.endsWith("/sign") -> {
                val document = documentOfVersion(path)
                if (document.status != "DRAFT" || request.json["jpaVersion"]!!.jsonPrimitive.long != document.jpaVersion) {
                    return conflict()
                }
                document.status = "SIGNED"
                ok("""{"id": "v-${document.id}", "status": "SIGNED"}""")
            }
            else -> error("unexpected request ${request.method.value} $path")
        }
    }

    private fun documentOfVersion(path: String): StoredDocument {
        val versionId = path.split("/")[4]
        return documents.single { "v-${it.id}" == versionId }
    }

    private fun detail(document: StoredDocument): String = """
        {
          "id": "${document.id}",
          "episodeId": "${document.episodeId}",
          "template": {"id": "tpl", "code": "X", "templateVersion": 1, "category": "${document.category}", "name": "X"},
          "documentStatus": "${document.status}",
          "documentDate": "${document.documentDate}",
          "versionHistory": [{"id": "v-${document.id}", "versionNumber": 1, "status": "${document.status}", "createdAt": "${document.documentDate}", "createdBy": {"id": "u1", "fullName": "${document.author}"}}],
          "latestVersion": {
            "id": "v-${document.id}",
            "documentId": "${document.id}",
            "versionNumber": 1,
            "status": "${document.status}",
            "content": ${document.content},
            "draftDeadline": ${document.draftDeadline?.let { "\"$it\"" }},
            "audit": {"createdAt": "${document.documentDate}", "jpaVersion": ${document.jpaVersion}}
          }
        }
    """.trimIndent()

    private fun page(content: List<String>) =
        """{"content": [${content.joinToString(",")}], "page": {"number": 0, "size": 100, "totalElements": ${content.size}, "totalPages": 1}}"""

    private fun MockRequestHandleScope.ok(body: String) = json(HttpStatusCode.OK, body)

    private fun MockRequestHandleScope.notFound() = json(HttpStatusCode.NotFound, """{"title": "Not Found"}""")

    private fun MockRequestHandleScope.conflict() = json(HttpStatusCode.Conflict, """{"title": "Conflict"}""")

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
}

private val filiacion = Filiacion(
    edad = "35",
    fechaNacimiento = "01/01/1990",
    estadoCivil = "Soltero(a)",
    sexo = "Masculino",
    dni = "70083906",
    gradoInstruccion = "Superior",
    ocupacion = "Docente",
    lugarNacimiento = "Lima",
    lugarProcedencia = "Lima",
    familiarResponsable = "Madre",
    direccion = "Av. Siempre Viva 123",
    servicioIngreso = "Psiquiatria",
)

private val diagnostico = Diagnostico("F32.2", "", "", "", "60")

private val filiacionContent = buildJsonObject { put("edad", "35"); put("dni", "70083906") }

private fun evolucion() = Evolucion(
    id = "ignored",
    fecha = "",
    hora = "",
    medico = "",
    vitals = Vitals("120/80", "80", "16", "36.5", "98", "21"),
    diagnosticos = listOf(DiagnosticoCie10("F32.2", "Episodio depresivo grave")),
    subjective = "Refiere mejoria del animo",
    objective = "Lucido, orientado",
    assessment = "Respuesta parcial",
    plan = "Continuar tratamiento",
    rx = "Sertralina 50 mg",
    pronostico = Pronostico.FAVORABLE,
    resultado = EvolucionResultado.FAVORABLE,
    examenes = emptyList(),
    examenesObs = "",
    status = NoteStatus.SIGNED,
    prescripciones = listOf(sertralina),
)

private val sertralina = Prescripcion(
    drugId = "drug-sertralina",
    drugName = "Sertralina 50 mg · Tableta",
    dose = "50 mg",
    frequency = "Cada 24 h",
    route = RouteOfAdministration.PO,
    duration = "30 dias",
    instructions = "Con el desayuno",
)

class KtorClinicalNoteRepositoryTest {

    // --- Historia Clinica: reads ---------------------------------------------------------

    @Test
    fun `a stay with no admission document has an empty, not-yet-started historia clinica`() = runTest {
        val backend = FakeDocumentBackend()
        val repository = KtorClinicalNoteRepository(backend.client())

        val historiaClinica = repository.getHistoriaClinica(HOSP_ID).first()

        assertNull(historiaClinica.status)
        assertFalse(historiaClinica.filiacion.complete)
        val folder = backend.sent.single()
        assertEquals("/api/v1/episodes/$HOSP_ID/documents", folder.path)
        assertEquals("ADMISSION", folder.query["category"])
    }

    @Test
    fun `an unknown stay has no historia clinica rather than failing`() = runTest {
        val repository = KtorClinicalNoteRepository(FakeDocumentBackend().client())

        assertNull(repository.getHistoriaClinica("unknown").first().status)
    }

    @Test
    fun `a section is complete exactly when its key is in the content`() = runTest {
        val backend = FakeDocumentBackend()
        backend.documents += StoredDocument(
            id = "hc",
            episodeId = HOSP_ID,
            category = "ADMISSION",
            content = buildJsonObject { put("filiacion", filiacionContent) },
            draftDeadline = "2026-06-24T16:00:00Z",
        )
        val repository = KtorClinicalNoteRepository(backend.client())

        val historiaClinica = repository.getHistoriaClinica(HOSP_ID).first()

        assertEquals(NoteStatus.DRAFT, historiaClinica.status)
        assertEquals(Instant.parse("2026-06-24T16:00:00Z"), historiaClinica.draftDeadline)
        assertTrue(historiaClinica.filiacion.complete)
        assertEquals("70083906", historiaClinica.filiacion.data?.dni)
        assertEquals("", historiaClinica.filiacion.data?.ocupacion, "a field the stored section lacks reads as blank")
        assertFalse(historiaClinica.motivoIngreso.complete)
    }

    @Test
    fun `a voided admission document is ignored`() = runTest {
        val backend = FakeDocumentBackend()
        backend.documents += StoredDocument(id = "hc", episodeId = HOSP_ID, category = "ADMISSION", status = "VOIDED", content = JsonObject(emptyMap()))
        val repository = KtorClinicalNoteRepository(backend.client())

        assertNull(repository.getHistoriaClinica(HOSP_ID).first().status)
    }

    // --- Historia Clinica: writes --------------------------------------------------------

    @Test
    fun `the first saved section creates the admission document from the HC_PSIQ template`() = runTest {
        val backend = FakeDocumentBackend()
        val repository = KtorClinicalNoteRepository(backend.client())

        repository.getHistoriaClinica(HOSP_ID).test {
            assertNull(awaitItem().status)

            repository.saveHistoriaClinicaSection(HOSP_ID, HcSectionKey.FILIACION, filiacion)

            val saved = awaitItem()
            assertEquals(NoteStatus.DRAFT, saved.status)
            assertEquals(filiacion, saved.filiacion.data)
            cancelAndIgnoreRemainingEvents()
        }
        val templates = backend.sent.single { it.path == "/api/v1/document-templates" }
        assertEquals("ADMISSION", templates.query["category"])
        assertEquals("INPATIENT", templates.query["episodeType"])
        val create = backend.sent.single { it.method == HttpMethod.Post }
        assertEquals("/api/v1/episodes/$HOSP_ID/documents", create.path)
        assertEquals(HC_TEMPLATE_ID, create.json["templateId"]!!.jsonPrimitive.content)
        val initialContent = create.json["initialContent"]!!.jsonObject
        assertEquals(setOf("filiacion"), initialContent.keys)
        assertEquals(12, initialContent["filiacion"]!!.jsonObject.size, "every field is written, blank or not")
    }

    @Test
    fun `a later section is merged into the stored content and sent with the token that was read`() = runTest {
        val backend = FakeDocumentBackend()
        backend.documents += StoredDocument(
            id = "hc",
            episodeId = HOSP_ID,
            category = "ADMISSION",
            content = buildJsonObject {
                put("filiacion", filiacionContent)
                put("antecedentes", buildJsonObject { put("written", "by a newer form") })
            },
            jpaVersion = 7,
        )
        val repository = KtorClinicalNoteRepository(backend.client())
        repository.getHistoriaClinica(HOSP_ID).first()

        repository.saveHistoriaClinicaSection(HOSP_ID, HcSectionKey.MOTIVO_INGRESO, MotivoIngreso(psicosis = true))
        repository.saveHistoriaClinicaSection(HOSP_ID, HcSectionKey.DIAGNOSTICO, diagnostico)

        val (first, second) = backend.sent.filter { it.method == HttpMethod.Put }
        assertEquals("/api/v1/document-versions/v-hc/draft", first.path)
        assertEquals(7, first.json["jpaVersion"]!!.jsonPrimitive.long)
        assertEquals(setOf("filiacion", "antecedentes", "motivoIngreso"), first.json["content"]!!.jsonObject.keys)
        assertTrue(first.json["content"]!!.jsonObject["motivoIngreso"]!!.jsonObject["psicosis"]!!.jsonPrimitive.boolean)
        assertEquals(8, second.json["jpaVersion"]!!.jsonPrimitive.long, "the next save sends the token the last one returned")
        assertEquals(setOf("filiacion", "antecedentes", "motivoIngreso", "diagnostico"), second.json["content"]!!.jsonObject.keys)
        val historiaClinica = repository.getHistoriaClinica(HOSP_ID).first()
        assertTrue(historiaClinica.motivoIngreso.complete && historiaClinica.diagnostico.complete)
    }

    @Test
    fun `a save after someone else's is a conflict, and the newer content is reloaded`() = runTest {
        val backend = FakeDocumentBackend()
        backend.documents += StoredDocument(id = "hc", episodeId = HOSP_ID, category = "ADMISSION", content = buildJsonObject { put("filiacion", filiacionContent) })
        val repository = KtorClinicalNoteRepository(backend.client())

        repository.getHistoriaClinica(HOSP_ID).test {
            assertFalse(awaitItem().plan.complete)
            backend.saveElsewhere("hc", buildJsonObject { put("filiacion", filiacionContent); put("plan", buildJsonObject { put("psicofarmacos", "VO") }) })

            val error = assertFailsWith<ApiException> {
                repository.saveHistoriaClinicaSection(HOSP_ID, HcSectionKey.DIAGNOSTICO, diagnostico)
            }

            assertTrue(error.error is ApiError.Conflict)
            val reloaded = awaitItem()
            assertEquals("VO", reloaded.plan.data?.psicofarmacos)
            assertFalse(reloaded.diagnostico.complete, "nothing is retried over the other edit")
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, backend.sent.count { it.method == HttpMethod.Put })
    }

    @Test
    fun `creating a second admission document reloads the one that won`() = runTest {
        val backend = FakeDocumentBackend()
        val repository = KtorClinicalNoteRepository(backend.client())
        repository.getHistoriaClinica(HOSP_ID).first()
        backend.documents += StoredDocument(id = "hc", episodeId = HOSP_ID, category = "ADMISSION", content = buildJsonObject { put("filiacion", filiacionContent) })

        val error = assertFailsWith<ApiException> {
            repository.saveHistoriaClinicaSection(HOSP_ID, HcSectionKey.DIAGNOSTICO, diagnostico)
        }

        assertTrue(error.error is ApiError.Conflict)
        assertEquals(NoteStatus.DRAFT, repository.getHistoriaClinica(HOSP_ID).first().status)
    }

    @Test
    fun `a signed historia clinica refuses further saves without asking the server`() = runTest {
        val backend = FakeDocumentBackend()
        backend.documents += StoredDocument(id = "hc", episodeId = HOSP_ID, category = "ADMISSION", status = "SIGNED", content = JsonObject(emptyMap()))
        val repository = KtorClinicalNoteRepository(backend.client())
        repository.getHistoriaClinica(HOSP_ID).first()

        assertFailsWith<ApiException> { repository.saveHistoriaClinicaSection(HOSP_ID, HcSectionKey.DIAGNOSTICO, diagnostico) }

        assertTrue(backend.sent.none { it.method == HttpMethod.Put })
    }

    @Test
    fun `a section of the wrong type is rejected`() = runTest {
        val repository = KtorClinicalNoteRepository(FakeDocumentBackend().client())

        assertFailsWith<IllegalArgumentException> {
            repository.saveHistoriaClinicaSection(HOSP_ID, HcSectionKey.FILIACION, diagnostico)
        }
    }

    @Test
    fun `signing sends the current token and the historia clinica reads back signed`() = runTest {
        val backend = FakeDocumentBackend()
        backend.documents += StoredDocument(id = "hc", episodeId = HOSP_ID, category = "ADMISSION", content = JsonObject(emptyMap()), jpaVersion = 3, draftDeadline = "2026-06-24T16:00:00Z")
        val repository = KtorClinicalNoteRepository(backend.client())

        repository.getHistoriaClinica(HOSP_ID).test {
            assertEquals(NoteStatus.DRAFT, awaitItem().status)

            repository.signHistoriaClinica(HOSP_ID)

            val signed = awaitItem()
            assertEquals(NoteStatus.SIGNED, signed.status)
            assertNull(signed.draftDeadline, "a signed note has no deadline left")
            cancelAndIgnoreRemainingEvents()
        }
        val sign = backend.sent.single { it.path.endsWith("/sign") }
        assertEquals("/api/v1/document-versions/v-hc/sign", sign.path)
        assertEquals(3, sign.json["jpaVersion"]!!.jsonPrimitive.long)
    }

    // --- Evoluciones ---------------------------------------------------------------------

    @Test
    fun `evoluciones are read one document each and listed newest first`() = runTest {
        val backend = FakeDocumentBackend()
        val repository = KtorClinicalNoteRepository(backend.client())
        val older = repository.addEvolucion(HOSP_ID, evolucion())
        backend.documents += StoredDocument(
            id = "newer",
            episodeId = HOSP_ID,
            category = "PROGRESS_NOTE",
            status = "SIGNED",
            documentDate = "2026-06-22T15:30:00Z",
            author = "Dr. Hirsh",
            content = buildJsonObject { put("subjective", "Sin cambios"); put("pronostico", "MALO") },
        )
        backend.sent.clear()

        val list = repository.getEvoluciones(HOSP_ID).first()

        assertEquals(listOf("newer", older.id), list.map { it.id })
        assertEquals(listOf("/api/v1/episodes/$HOSP_ID/documents"), backend.sent.filter { "documents" in it.path && "clinical" !in it.path }.map { it.path })
        val newer = list.first()
        assertEquals("Dr. Hirsh", newer.medico)
        assertEquals(NoteStatus.SIGNED, newer.status)
        assertEquals(Pronostico.MALO, newer.pronostico)
        assertEquals(EvolucionResultado.ESTACIONARIA, newer.resultado, "a missing outcome falls back instead of failing the list")
        assertEquals(NoteStatus.DRAFT, list.last().status)
    }

    @Test
    fun `a new evolucion is created as a draft from the EVOL_PSIQ template and joins an open list`() = runTest {
        val backend = FakeDocumentBackend()
        val repository = KtorClinicalNoteRepository(backend.client())

        repository.getEvoluciones(HOSP_ID).test {
            assertEquals(emptyList(), awaitItem())

            val created = repository.addEvolucion(HOSP_ID, evolucion())

            assertEquals(NoteStatus.DRAFT, created.status)
            assertEquals("Dra. Rosa Quispe", created.medico)
            assertEquals(listOf(sertralina), created.prescripciones, "prescriptions read back as they were sent")
            assertEquals(listOf(created), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        val create = backend.sent.single { it.method == HttpMethod.Post }
        assertEquals(EVOL_TEMPLATE_ID, create.json["templateId"]!!.jsonPrimitive.content)
        val content = create.json["initialContent"]!!.jsonObject
        assertEquals("FAVORABLE", content["pronostico"]!!.jsonPrimitive.content)
        assertEquals("120/80", content["vitals"]!!.jsonObject["pa"]!!.jsonPrimitive.content)
        assertEquals("F32.2", content["diagnosticos"]!!.jsonArray.single().jsonObject["codigoCie10"]!!.jsonPrimitive.content)
        // The paths the backend's treatment extraction reads (EVOL_PSIQ v2).
        val prescription = content["prescriptions"]!!.jsonArray.single().jsonObject
        assertEquals("drug-sertralina", prescription["drug"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("Sertralina 50 mg · Tableta", prescription["drug"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("50 mg", prescription["dose"]!!.jsonPrimitive.content)
        assertEquals("Cada 24 h", prescription["frequency"]!!.jsonPrimitive.content)
        assertEquals("PO", prescription["route"]!!.jsonPrimitive.content)
    }

    @Test
    fun `signing an evolucion updates both the open list and the open note`() = runTest {
        val backend = FakeDocumentBackend()
        val repository = KtorClinicalNoteRepository(backend.client())
        val created = repository.addEvolucion(HOSP_ID, evolucion())

        turbineScope {
            val signatures = repository.signatures.testIn(backgroundScope)
            repository.getEvolucion(HOSP_ID, created.id).test {
                assertEquals(NoteStatus.DRAFT, awaitItem()?.status)

                repository.signEvolucion(HOSP_ID, created.id)

                assertEquals(NoteStatus.SIGNED, awaitItem()?.status)
                cancelAndIgnoreRemainingEvents()
            }
            signatures.awaitItem()
            signatures.cancel()
        }
        assertEquals(NoteStatus.SIGNED, repository.getEvoluciones(HOSP_ID).first().single().status)
    }

    @Test
    fun `an evolucion of another stay is not found`() = runTest {
        val backend = FakeDocumentBackend()
        val repository = KtorClinicalNoteRepository(backend.client())
        val created = repository.addEvolucion(OTHER_HOSP_ID, evolucion())

        assertNull(repository.getEvolucion(HOSP_ID, created.id).first())
        assertFailsWith<ApiException> { repository.signEvolucion(HOSP_ID, created.id) }
    }
}

