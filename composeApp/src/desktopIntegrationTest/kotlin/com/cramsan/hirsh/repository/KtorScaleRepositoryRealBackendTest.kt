package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.ScaleSeverity
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.network.ApiConfig
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
 * [KtorScaleRepository] against a real, separately-running backend (see
 * `.claude/skills/_shared/real-backend-check.md`, Tier 1): proves the scale structure, answer
 * format and alert flag the client parses are the ones the backend actually serves and scores.
 *
 * Needs the seeded PHQ-9 template and the backend's answer validation (hirsh PR #19). Self-contained
 * otherwise: creates its own service, bed, patient, stay and evolucion. Skips when the backend isn't
 * reachable or the credentials aren't set.
 */
class KtorScaleRepositoryRealBackendTest {

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
    fun scoresAndAppliesPhq9AndRaisesTheSuicideRiskAlert() = runTest {
        val client = realHttpClient()
        val hospId = admitNewPatient(client)
        val evolucion = KtorClinicalNoteRepository(client).addEvolucion(hospId, sampleEvolucion)
        val repository = KtorScaleRepository(client)

        val summary = repository.getScales().single { it.code == "PHQ-9" }
        val phq9 = repository.getScale(summary.id)
        assertEquals((1..9).map { "q$it" }, phq9.questions.map { it.id })
        assertTrue(phq9.questions.all { question -> question.options.map { it.value } == listOf(0, 1, 2, 3) })

        val partial = repository.previewScore(phq9.id, mapOf("q1" to 2, "q2" to 1))
        assertEquals(3.0, partial.total)
        assertEquals(false, partial.raisesSuicideAlert)

        assertFailsWith<ApiException>("an incomplete scale can't be applied") {
            repository.applyScale(hospId, evolucion.id, phq9.id, mapOf("q1" to 2), null)
        }

        val answers = (1..9).associate { "q$it" to if (it == 9) 1 else 2 }
        assertEquals(true, repository.previewScore(phq9.id, answers).raisesSuicideAlert)
        val applied = repository.applyScale(hospId, evolucion.id, phq9.id, answers, "Refiere ideacion pasiva")
        assertEquals(evolucion.id, applied.documentId)
        assertEquals("PHQ-9", applied.scaleCode)
        assertEquals(17.0, applied.score.total)
        assertEquals(ScaleSeverity.MOD_SEVERE, applied.score.severity)
        assertEquals(true, applied.score.raisesSuicideAlert)

        // A fresh client sees it, read only from the backend.
        val stay = KtorScaleRepository(client).getAppliedScales(hospId).first()
        assertEquals(listOf(applied.id), stay.map { it.id })
        assertEquals(true, stay.single().score.raisesSuicideAlert)
    }

    private suspend fun admitNewPatient(client: HttpClient): String {
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username!!, password!!))
        }.body<LoginResponse>()
        val suffix = Random.nextInt(100_000, 999_999).toString()
        val serviceJson = client.postJson(
            "/api/v1/clinical-services",
            buildJsonObject {
                put("code", "SCAL-$suffix")
                put("name", "Servicio escalas $suffix")
                put("type", "INPATIENT")
            },
        )
        val service = ServiceRef(serviceJson.string("id"), serviceJson.string("name"))
        val bedJson = client.postJson("/api/v1/clinical-services/${service.id}/beds", buildJsonObject { put("number", "01") })
        val patient = KtorPatientRepository(client).addPatient(
            medicalRecordNumber = "HC-SCAL-$suffix",
            firstName = "Escalas",
            lastName = "Prueba",
            secondLastName = "",
            documentType = DocumentType.NID,
            documentNumber = "7$suffix",
            birthDate = "12/04/1991",
            phone = "",
            sex = Sex.FEMALE,
            bloodType = "",
        )
        return KtorEpisodeRepository(client).addHospitalization(
            patient.id,
            service,
            BedRef(bedJson.string("id"), bedJson.string("number")),
            UserRef(login.userId, login.username),
            "Ingreso de prueba",
        ).id
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
            subjective = "Refiere ideas de muerte pasivas.",
            objective = "Afecto aplanado.",
            assessment = "Episodio depresivo grave.",
            plan = "Aplicar PHQ-9.",
            rx = "Sertralina 50 mg",
            pronostico = Pronostico.RESERVADO,
            resultado = EvolucionResultado.ESTACIONARIA,
            examenes = emptyList(),
            examenesObs = "",
        )
    }
}
