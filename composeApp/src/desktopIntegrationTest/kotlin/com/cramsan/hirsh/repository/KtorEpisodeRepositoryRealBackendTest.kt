package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DischargeCondition
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DischargeType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [KtorEpisodeRepository] against a real, separately-running backend (see
 * `.claude/skills/_shared/real-backend-check.md`, Tier 1): the mock-engine tests in `commonTest`
 * only prove the client decodes the JSON *we* wrote; this proves it decodes what the backend
 * actually sends (offset timestamps, the patient header's extra fields, the 409 problem body).
 *
 * Self-contained: logs in with `HIRSH_ADMIN_USERNAME`/`HIRSH_ADMIN_PASSWORD`, then creates its own
 * service, bed and patient so it never depends on seed data. Skips when the backend isn't
 * reachable or the credentials aren't set.
 */
class KtorEpisodeRepositoryRealBackendTest {

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
    fun admitsListsFetchesAndDischargesAStayAndRefusesASecondOne() = runTest {
        val client = realHttpClient()
        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username!!, password!!))
        }.body<LoginResponse>()
        val physician = UserRef(login.userId, login.username)

        val suffix = Random.nextInt(100_000, 999_999).toString()
        val serviceJson = client.postJson(
            "/api/v1/clinical-services",
            buildJsonObject {
                put("code", "E2E-$suffix")
                put("name", "Servicio E2E $suffix")
                put("type", "INPATIENT")
            },
        )
        val service = ServiceRef(serviceJson.string("id"), serviceJson.string("name"))
        val bedJson = client.postJson("/api/v1/clinical-services/${service.id}/beds", buildJsonObject { put("number", "01") })
        val bed = BedRef(bedJson.string("id"), bedJson.string("number"))
        val patient = KtorPatientRepository(client).addPatient(
            medicalRecordNumber = "HC-E2E-$suffix",
            firstName = "Episodio",
            lastName = "Prueba",
            secondLastName = "",
            documentType = DocumentType.NID,
            documentNumber = "9$suffix",
            birthDate = "12/04/1991",
            phone = "",
            sex = Sex.FEMALE,
            bloodType = "",
        )

        val repository = KtorEpisodeRepository(client)
        val created = repository.addHospitalization(patient.id, service, bed, physician, "Ingreso de prueba")

        assertEquals(EpisodeStatus.ACTIVE, created.status)
        assertEquals(patient.id, created.patientId)
        assertEquals(service, created.currentService)
        assertEquals(bed, created.currentBed)
        assertEquals("Ingreso de prueba", created.admissionReason)

        val listed = repository.getHospitalizations(patient.id).first()
        assertEquals(listOf(created.id), listed.map { it.id })
        assertEquals(bed, listed.single().currentBed)
        assertEquals("Ingreso de prueba", listed.single().admissionReason)

        val fetched = assertNotNull(repository.getHospitalization(patient.id, created.id).first())
        // The POST's body must already be the stored episode: before HCEM-73 it carried a stale
        // jpaVersion once a bed was assigned, and nanoseconds Postgres doesn't keep.
        assertEquals(created, fetched)
        assertNull(repository.getHospitalization(OTHER_PATIENT_ID, created.id).first())

        val conflict = assertFailsWith<ApiException> {
            repository.addHospitalization(patient.id, service, null, physician, null)
        }
        assertEquals(ApiError.Conflict(created.id), conflict.error)

        // Discharge with a diagnosis the typeahead actually finds in the catalog. TRANSFER, since
        // it's the one type that doesn't end CLOSED.
        val diagnosis = KtorIcd10Repository(client).search("F32").first()
        val details = DischargeDetails(
            type = DischargeType.TRANSFER,
            condition = DischargeCondition.IMPROVED,
            diagnosisIcd10 = diagnosis.code,
            followUpPlan = "Control ambulatorio",
            nextAppointmentDate = "17/07/2030",
        )
        repository.discharge(created.id, details, fetched.jpaVersion)

        val discharged = assertNotNull(repository.getHospitalization(patient.id, created.id).first())
        assertEquals(EpisodeStatus.TRANSFERRED, discharged.status)
        assertNotNull(discharged.endDate)
        assertEquals(details, discharged.discharge)

        val stale = assertFailsWith<ApiException> { repository.discharge(created.id, details, fetched.jpaVersion) }
        assertEquals(ApiError.Conflict::class, stale.error::class)
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private companion object {
        const val OTHER_PATIENT_ID = "00000000-0000-4000-8000-000000000000"
    }
}
