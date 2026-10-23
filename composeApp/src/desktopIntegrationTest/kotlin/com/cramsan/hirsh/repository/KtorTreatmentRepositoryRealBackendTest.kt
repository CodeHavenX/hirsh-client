package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Prescripcion
import com.cramsan.hirsh.model.RouteOfAdministration
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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
import kotlin.time.Clock

/**
 * [KtorTreatmentRepository] against a real, separately-running backend (see
 * `.claude/skills/_shared/real-backend-check.md`, Tier 1): proves a prescription the client writes
 * into an evolucion is what the backend extracts as an active treatment when the note is signed,
 * and that suspending it takes it off the list.
 *
 * Needs the EVOL_PSIQ v2 template and the placeholder formulary (the hcem-11 backend branch), and an
 * admin with a medical license so it can sign. Self-contained otherwise: creates its own service,
 * bed, patient and stay. Skips when the backend isn't reachable or the credentials aren't set.
 */
class KtorTreatmentRepositoryRealBackendTest {

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
    fun aSignedPrescriptionBecomesAnActiveTreatmentUntilItIsSuspended() = runTest {
        val client = realHttpClient()
        val (patientId, hospId) = admitNewPatient(client)
        val notes = KtorClinicalNoteRepository(client)
        val repository = KtorTreatmentRepository(client, notes)
        assertEquals(emptyList(), repository.getActiveTreatments(patientId).first())

        val sertralina = KtorDrugRepository(client).search("Sertralina").first()
        val prescription = Prescripcion(
            drugId = sertralina.id,
            drugName = sertralina.displayName,
            dose = "50 mg",
            frequency = "cada 24 h",
            route = RouteOfAdministration.PO,
            duration = "30 dias",
            instructions = "Con el desayuno",
        )
        val evolucion = notes.addEvolucion(hospId, sampleEvolucion.copy(prescripciones = listOf(prescription)))
        assertEquals(emptyList(), repository.getActiveTreatments(patientId).first(), "a draft's prescriptions aren't treatments yet")

        notes.signEvolucion(hospId, evolucion.id)

        val treatment = repository.getActiveTreatments(patientId).first().single()
        assertEquals(sertralina.displayName, treatment.drugName)
        assertEquals("50 mg", treatment.dose)
        assertEquals("cada 24 h", treatment.frequency)
        assertEquals(RouteOfAdministration.PO, treatment.route)
        assertEquals(hospId, treatment.episodeId)

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        repository.suspend(treatment.id, "Sedacion excesiva", today)
        assertEquals(emptyList(), repository.getActiveTreatments(patientId).first())

        val again = assertFailsWith<ApiException>("a suspended treatment can't be suspended again") {
            repository.suspend(treatment.id, "Sedacion excesiva", today)
        }
        assertTrue(again.error is ApiError.Conflict)
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
                put("code", "TRAT-$suffix")
                put("name", "Servicio tratamientos $suffix")
                put("type", "INPATIENT")
            },
        )
        val service = ServiceRef(serviceJson.string("id"), serviceJson.string("name"))
        val bedJson = client.postJson("/api/v1/clinical-services/${service.id}/beds", buildJsonObject { put("number", "01") })
        val patient = KtorPatientRepository(client).addPatient(
            medicalRecordNumber = "HC-TRAT-$suffix",
            firstName = "Tratamientos",
            lastName = "Prueba",
            secondLastName = "",
            documentType = DocumentType.NID,
            documentNumber = "6$suffix",
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
        return patient.id to hospId
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
            subjective = "Refiere mejor descanso.",
            objective = "Afecto menos aplanado.",
            assessment = "Respuesta parcial.",
            plan = "Iniciar ISRS.",
            rx = "",
            pronostico = Pronostico.FAVORABLE,
            resultado = EvolucionResultado.FAVORABLE,
            examenes = emptyList(),
            examenesObs = "",
        )
    }
}
