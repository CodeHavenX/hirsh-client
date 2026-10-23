package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.ExamenRegional
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.model.isReadyToSign
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
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodedPath
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
import kotlin.test.assertTrue

/**
 * [KtorClinicalNoteRepository] against a real, separately-running backend (see
 * `.claude/skills/_shared/real-backend-check.md`, Tier 1): the mock-engine tests only prove the
 * client agrees with the document shapes *we* wrote down; this proves the backend's templates,
 * lock tokens and signing accept them.
 *
 * Needs the backend's `HC_PSIQ`/`EVOL_PSIQ` templates (hirsh PR #17) and an admin account with a
 * medical license -- the backend refuses a signature from an account without one. Self-contained
 * otherwise: creates its own service, bed, patient and stay. Skips when the backend isn't
 * reachable or the credentials aren't set.
 */
class KtorClinicalNoteRepositoryRealBackendTest {

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
    fun writesSignsAndReadsBackAHistoriaClinicaAndAnEvolucion() = runTest {
        val client = realHttpClient()
        val hospId = admitNewPatient(client)
        val repository = KtorClinicalNoteRepository(client)

        assertNull(repository.getHistoriaClinica(hospId).first().status, "a new stay has no Historia Clinica yet")

        repository.saveHistoriaClinicaSection(hospId, HcSectionKey.FILIACION, filiacion)
        val draft = repository.getHistoriaClinica(hospId).first()
        assertEquals(NoteStatus.DRAFT, draft.status)
        assertNotNull(draft.draftDeadline)
        assertEquals(filiacion, draft.filiacion.data)

        // A second client that read the draft before this save holds a stale lock token.
        val otherClient = KtorClinicalNoteRepository(client)
        otherClient.getHistoriaClinica(hospId).first()
        repository.saveHistoriaClinicaSection(hospId, HcSectionKey.MOTIVO_INGRESO, MotivoIngreso(psicosis = true))
        val stale = assertFailsWith<ApiException> {
            otherClient.saveHistoriaClinicaSection(hospId, HcSectionKey.PLAN, plan)
        }
        assertEquals(ApiError.Conflict::class, stale.error::class)

        repository.saveHistoriaClinicaSection(hospId, HcSectionKey.ENFERMEDAD_ACTUAL, enfermedadActual)
        repository.saveHistoriaClinicaSection(hospId, HcSectionKey.EXAMEN_FISICO, examenFisico)
        repository.saveHistoriaClinicaSection(hospId, HcSectionKey.DIAGNOSTICO, diagnostico)
        repository.saveHistoriaClinicaSection(hospId, HcSectionKey.PLAN, plan)
        val complete = repository.getHistoriaClinica(hospId).first()
        assertEquals(true, complete.isReadyToSign())
        assertEquals(MotivoIngreso(psicosis = true), complete.motivoIngreso.data)
        assertEquals(examenFisico, complete.examenFisico.data)

        repository.signHistoriaClinica(hospId)
        assertEquals(NoteStatus.SIGNED, repository.getHistoriaClinica(hospId).first().status)
        assertFailsWith<ApiException> {
            repository.saveHistoriaClinicaSection(hospId, HcSectionKey.PLAN, plan)
        }

        val created = repository.addEvolucion(hospId, evolucion)
        assertEquals(NoteStatus.DRAFT, created.status)
        assertEquals(listOf(created.id), repository.getEvoluciones(hospId).first().map { it.id })

        repository.signEvolucion(hospId, created.id)
        val signed = assertNotNull(repository.getEvolucion(hospId, created.id).first())
        assertEquals(NoteStatus.SIGNED, signed.status)
        assertEquals(evolucion.subjective, signed.subjective)
        assertEquals(evolucion.diagnosticos, signed.diagnosticos)
        assertEquals(evolucion.vitals, signed.vitals)
        assertEquals(evolucion.pronostico, signed.pronostico)
        assertEquals(evolucion.resultado, signed.resultado)

        // A fresh client sees the same notes, read only from the backend -- and only through the
        // folder, which carries each note's content (HCEM-77).
        val documentReads = mutableListOf<String>()
        client.plugin(HttpSend).intercept { request ->
            if ("/clinical-documents/" in request.url.encodedPath) documentReads += request.url.encodedPath
            execute(request)
        }
        val fresh = KtorClinicalNoteRepository(client)
        assertEquals(NoteStatus.SIGNED, fresh.getHistoriaClinica(hospId).first().status)
        val listed = fresh.getEvoluciones(hospId).first()
        assertEquals(listOf(NoteStatus.SIGNED), listed.map { it.status })
        assertEquals(evolucion.diagnosticos, listed.single().diagnosticos)
        assertTrue(listed.single().medico.isNotBlank(), "the author comes with the folder entry")
        assertEquals(emptyList(), documentReads)
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
                put("code", "NOTE-$suffix")
                put("name", "Servicio notas $suffix")
                put("type", "INPATIENT")
            },
        )
        val service = ServiceRef(serviceJson.string("id"), serviceJson.string("name"))
        val bedJson = client.postJson("/api/v1/clinical-services/${service.id}/beds", buildJsonObject { put("number", "01") })
        val patient = KtorPatientRepository(client).addPatient(
            medicalRecordNumber = "HC-NOTE-$suffix",
            firstName = "Notas",
            lastName = "Prueba",
            secondLastName = "",
            documentType = DocumentType.NID,
            documentNumber = "8$suffix",
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
        val filiacion = Filiacion(
            edad = "34 anos",
            fechaNacimiento = "12/04/1991",
            estadoCivil = "Soltera",
            sexo = "Femenino",
            dni = "70083906",
            gradoInstruccion = "Superior",
            ocupacion = "Docente",
            lugarNacimiento = "Lima",
            lugarProcedencia = "Lima",
            familiarResponsable = "Madre",
            direccion = "Av. Prueba 123",
            servicioIngreso = "UHSMA",
        )
        val enfermedadActual = EnfermedadActual("1 mes", "Insidioso", "Progresivo", "2 semanas", "Relato de prueba")
        val examenFisico = ExamenFisico(
            pa = "110/70",
            fc = "80",
            fr = "18",
            temp = "36.5",
            peso = "60",
            talla = "1.60",
            imc = "23.4",
            estadoGeneral = "Regular",
            examenRegional = ExamenRegional("Normal", "MV pasa bien", "RCR", "Blando", "Sin focalizacion"),
        )
        val diagnostico = Diagnostico("F32.2", "Diferido", "Ninguno", "Familiar", "50")
        val plan = Plan("UHSMA", "Hemograma", "Sertralina 50 mg", "Psicologia")
        val evolucion = Evolucion(
            id = "",
            fecha = "",
            hora = "",
            medico = "",
            vitals = Vitals(pa = "110/70", fc = "78", fr = "18", temp = "36.6", satO2 = "98", fio2 = "21"),
            diagnosticos = listOf(DiagnosticoCie10("F32.2", "Episodio depresivo grave")),
            subjective = "Refiere mejor descanso.",
            objective = "Afecto menos aplanado.",
            assessment = "Respuesta parcial.",
            plan = "Mantener esquema.",
            rx = "Sertralina 50 mg",
            pronostico = Pronostico.FAVORABLE,
            resultado = EvolucionResultado.FAVORABLE,
            examenes = emptyList(),
            examenesObs = "",
        )
    }
}
