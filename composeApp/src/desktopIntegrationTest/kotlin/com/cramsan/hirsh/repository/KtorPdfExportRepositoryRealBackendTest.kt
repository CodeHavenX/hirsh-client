package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.network.ApiConfig
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.XsrfHeaderPlugin
import com.cramsan.hirsh.network.dto.LoginRequest
import com.cramsan.hirsh.network.dto.LoginResponse
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [KtorPdfExportRepository] against a real, separately-running backend (see
 * `.claude/skills/_shared/real-backend-check.md`, Tier 1): proves the export is queued, polled
 * and downloaded the way the backend actually serves it, and that the file is a readable rendering
 * of the signed note rather than a raw dump.
 *
 * Needs the readable-export renderer (hirsh PR for HCEM-14) and an admin with a medical license so
 * it can sign. Runs in real time (runBlocking), since the backend generates in the background.
 * Skips when the backend isn't reachable or the credentials aren't set.
 */
class KtorPdfExportRepositoryRealBackendTest {

    private val username: String? = System.getenv("HIRSH_ADMIN_USERNAME")
    private val password: String? = System.getenv("HIRSH_ADMIN_PASSWORD")

    @Before
    fun checkBackendReachable() {
        val reachable = try {
            (URI("${ApiConfig.BASE_URL}/actuator/health").toURL().openConnection() as HttpURLConnection)
                .apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                }
                .responseCode
            true
        } catch (e: Exception) {
            false
        }
        assumeTrue("No backend reachable at ${ApiConfig.BASE_URL}", reachable)
        assumeTrue("HIRSH_ADMIN_USERNAME/HIRSH_ADMIN_PASSWORD are not set", username != null && password != null)
    }

    /** The exact plugin stack from `di/AppModule.kt`. */
    private fun realHttpClient() = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        install(Logging) { level = LogLevel.INFO }
        install(HttpCookies)
        install(XsrfHeaderPlugin)
        installApiErrorValidator(onUnauthorized = {})
        defaultRequest { url(ApiConfig.BASE_URL) }
    }

    private suspend fun HttpClient.postJson(path: String, body: JsonObject): JsonObject = post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()

    @Test
    fun exportsASignedEvolucionAndTheFullRecordAsReadablePdfs() = runBlocking {
        val client = realHttpClient()
        val (record, hospId) = admitNewPatient(client)
        val notes = KtorClinicalNoteRepository(client)
        val repository = KtorPdfExportRepository(client, notes)

        val draft = notes.addEvolucion(hospId, sampleEvolucion)
        assertFailsWith<ApiException>("a draft can't be exported") {
            repository.export(PdfExportTarget.Evolucion(hospId, draft.id))
        }.also { assertTrue(it.error is ApiError.Conflict, it.error.toString()) }
        notes.signEvolucion(hospId, draft.id)

        val note = repository.export(PdfExportTarget.Evolucion(hospId, draft.id))
        assertTrue(note.filename.startsWith("evolucion-$record-"), note.filename)
        val text = note.bytes.toString(Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("(Subjetivo) Tj"), "laid out under the form's labels")
        assertTrue(text.contains("(Refiere mejoría con el tratamiento.) Tj"), "accents survive")

        val fullRecord = repository.export(PdfExportTarget.FullRecord(hospId))
        assertEquals(true, fullRecord.filename.startsWith("registro-completo-$record-"), fullRecord.filename)
    }

    private suspend fun admitNewPatient(client: HttpClient): Pair<String, String> {
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username!!, password!!))
        }.body<LoginResponse>()
        val suffix = Random.nextInt(100_000, 999_999).toString()
        val serviceJson = client.postJson(
            "/api/v1/clinical-services",
            buildJsonObject {
                put("code", "PDF-$suffix")
                put("name", "Servicio PDF $suffix")
                put("type", "INPATIENT")
            },
        )
        val service = ServiceRef(serviceJson.string("id"), serviceJson.string("name"))
        val bedJson = client.postJson("/api/v1/clinical-services/${service.id}/beds", buildJsonObject { put("number", "01") })
        val record = "HC-PDF-$suffix"
        val patient = KtorPatientRepository(client).addPatient(
            medicalRecordNumber = record,
            firstName = "Exportacion",
            lastName = "Prueba",
            secondLastName = "",
            documentType = DocumentType.NID,
            documentNumber = "5$suffix",
            birthDate = "12/04/1991",
            phone = "",
            sex = Sex.FEMALE,
            bloodType = "",
        )
        val hospId = KtorEpisodeRepository(client).addHospitalization(
            patient.id,
            service,
            BedRef(bedJson.string("id"), bedJson.string("number")),
            UserRef(login.userId, login.username),
            "Ingreso de prueba",
        ).id
        return record to hospId
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private companion object {
        val sampleEvolucion = Evolucion(
            id = "",
            fecha = "",
            hora = "",
            medico = "",
            vitals = Vitals(pa = "110/70", fc = "78", fr = "18", temp = "36.6", satO2 = "98", fio2 = "21"),
            diagnosticos = listOf(DiagnosticoCie10("F32.2", "Episodio depresivo grave")),
            subjective = "Refiere mejoría con el tratamiento.",
            objective = "Afecto menos aplanado.",
            assessment = "Respuesta parcial.",
            plan = "Mantener esquema.",
            rx = "",
            pronostico = Pronostico.FAVORABLE,
            resultado = EvolucionResultado.FAVORABLE,
            examenes = emptyList(),
            examenesObs = "",
        )
    }
}
