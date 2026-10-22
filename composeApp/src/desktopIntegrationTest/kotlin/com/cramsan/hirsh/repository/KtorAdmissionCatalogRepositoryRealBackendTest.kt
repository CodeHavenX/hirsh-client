package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.network.ApiConfig
import com.cramsan.hirsh.network.XsrfHeaderPlugin
import com.cramsan.hirsh.network.dto.LoginRequest
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [KtorAdmissionCatalogRepository] against a real backend (Tier 1 of
 * `.claude/skills/_shared/real-backend-check.md`): creates a service with two beds, occupies one
 * through a real admission, deactivates another service, and adds a physician, then checks that
 * each picker query returns exactly what the Admision form may offer. Skips like
 * [KtorEpisodeRepositoryRealBackendTest] when there's no backend or no credentials.
 */
class KtorAdmissionCatalogRepositoryRealBackendTest {

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
    fun offersActiveServicesFreeBedsAndActivePhysiciansOnly() = runTest {
        val client = realHttpClient()
        client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username!!, password!!))
        }
        val suffix = Random.nextInt(100_000, 999_999).toString()

        fun service(code: String) = buildJsonObject {
            put("code", code)
            put("name", "Servicio $code")
            put("type", "INPATIENT")
        }
        val serviceId = client.postJson("/api/v1/clinical-services", service("CAT-$suffix")).string("id")
        val closedId = client.postJson("/api/v1/clinical-services", service("CLS-$suffix")).string("id")
        client.patch("/api/v1/clinical-services/$closedId") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("active", false) })
        }
        val freeBed = client.postJson("/api/v1/clinical-services/$serviceId/beds", buildJsonObject { put("number", "01") })
        val takenBed = client.postJson("/api/v1/clinical-services/$serviceId/beds", buildJsonObject { put("number", "02") })

        val physicianJson = client.postJson(
            "/api/v1/users",
            buildJsonObject {
                put("username", "cat$suffix")
                put("temporaryPassword", "Temp0ral-$suffix")
                put("firstName", "Medica")
                put("lastName", "Catalogo")
                put("documentNumber", "6$suffix")
                putJsonArray("roleAssignments") { add(buildJsonObject { put("roleCode", "ATTENDING_PHYSICIAN") }) }
            },
        )
        val physician = UserRef(physicianJson.string("id"), physicianJson.string("fullName"))

        val patient = client.postJson(
            "/api/v1/patients",
            buildJsonObject {
                put("medicalRecordNumber", "HC-CAT-$suffix")
                put("documentType", "NID")
                put("documentNumber", "5$suffix")
                put("firstName", "Cama")
                put("lastName", "Ocupada")
                put("birthDate", "1990-01-01")
                put("sex", "F")
            },
        )
        client.postJson(
            "/api/v1/episodes",
            buildJsonObject {
                put("patientId", patient.string("id"))
                put("episodeType", "INPATIENT")
                put("attendingPhysicianId", physician.id)
                put("serviceId", serviceId)
                put("bedId", takenBed.string("id"))
            },
        )

        val repository = KtorAdmissionCatalogRepository(client)

        val serviceIds = repository.services().map { it.id }
        assertTrue(serviceId in serviceIds)
        assertFalse(closedId in serviceIds, "a deactivated service must not be offered")
        assertEquals(listOf(freeBed.string("id")), repository.availableBeds(serviceId).map { it.id })
        assertTrue(physician in repository.attendingPhysicians())
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
