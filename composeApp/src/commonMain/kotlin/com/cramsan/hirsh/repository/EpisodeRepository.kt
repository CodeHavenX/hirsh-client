package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.DischargeDetails
import com.cramsan.hirsh.model.DischargeType
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.getAllPages
import com.cramsan.hirsh.network.dto.CreateEpisodeRequest
import com.cramsan.hirsh.network.dto.EpisodeResponse
import com.cramsan.hirsh.network.dto.EpisodeSummary
import com.cramsan.hirsh.network.dto.EpisodeTypeDto
import com.cramsan.hirsh.network.dto.toRequest
import com.cramsan.hirsh.network.dto.toDomain
import com.cramsan.hirsh.util.Clock
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/**
 * Hospital stays -- the backend's `INPATIENT` episodes. The real/fake boundary is this type
 * (HISS-641): everything here has a real endpoint, while a stay's Historia Clinica and evoluciones
 * live in [ClinicalNoteRepository], which has none yet.
 */
interface EpisodeRepository {
    fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>>

    /** Resolves by [patientId] and [hospId] together -- a hospId that exists but belongs to a different patient is not-found, never a fallback. */
    fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?>

    /**
     * [Hospitalizacion.startDate] is stamped to now (this repository's [Clock]) -- callers never
     * supply it. Takes references rather than bare ids so the in-memory implementation can show
     * names; the real `CreateEpisodeRequest` only sends their ids (HISS-632).
     *
     * A patient may have only one [EpisodeStatus.ACTIVE] stay: admitting a second throws
     * [com.cramsan.hirsh.network.ApiException] wrapping
     * [com.cramsan.hirsh.network.ApiError.Conflict] with the open stay's id, as the backend's `409`
     * does.
     */
    suspend fun addHospitalization(
        patientId: String,
        service: ServiceRef,
        bed: BedRef?,
        attendingPhysician: UserRef,
        admissionReason: String?,
    ): Hospitalizacion

    /**
     * Closes the episode with [details] and stamps [Hospitalizacion.endDate] to now. The status
     * becomes [EpisodeStatus.TRANSFERRED] for a [DischargeType.TRANSFER] and [EpisodeStatus.CLOSED]
     * otherwise, as the backend does.
     *
     * [jpaVersion] is the version the caller last read -- a mismatch against the currently stored
     * version throws [com.cramsan.hirsh.network.ApiException] wrapping
     * [com.cramsan.hirsh.network.ApiError.Conflict] instead of discharging over a concurrent edit
     * (HISS-604).
     */
    suspend fun discharge(hospId: String, details: DischargeDetails, jpaVersion: Long)
}

/**
 * In-memory stand-in, seeded from prototype/shared/data.js's HOSPITALIZATIONS. No longer bound
 * since the admission form gained real pickers (HISS-634); kept for tests that need seeded stays.
 *
 * A single flat [MutableStateFlow] rather than one per patient -- every mutating call updates this
 * same backing state via [MutableStateFlow.update], and every read is a derived [Flow] off it, per
 * HISS-112 ([PatientRepository]/[InMemoryPatientRepository]).
 */
class InMemoryEpisodeRepository(
    private val clock: Clock,
) : EpisodeRepository {

    private val _hospitalizations = MutableStateFlow(seedHospitalizations())
    val hospitalizations: StateFlow<List<Hospitalizacion>> = _hospitalizations.asStateFlow()

    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> =
        hospitalizations.map { list -> list.filter { it.patientId == patientId } }

    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> =
        hospitalizations.map { list -> list.find { it.patientId == patientId && it.id == hospId } }

    override suspend fun addHospitalization(
        patientId: String,
        service: ServiceRef,
        bed: BedRef?,
        attendingPhysician: UserRef,
        admissionReason: String?,
    ): Hospitalizacion {
        _hospitalizations.value.find { it.patientId == patientId && it.status == EpisodeStatus.ACTIVE }?.let {
            throw ApiException(ApiError.Conflict(it.id))
        }
        val newHospitalization = Hospitalizacion(
            id = nextId("h_"),
            patientId = patientId,
            currentService = service,
            currentBed = bed,
            attendingPhysician = attendingPhysician,
            startDate = clock.now(),
            endDate = null,
            admissionReason = admissionReason,
            status = EpisodeStatus.ACTIVE,
        )
        _hospitalizations.update { it + newHospitalization }
        return newHospitalization
    }

    /** Throws [ApiError.NotFound] for an unknown [hospId], like the backend. */
    override suspend fun discharge(hospId: String, details: DischargeDetails, jpaVersion: Long) {
        val current = _hospitalizations.value.find { it.id == hospId } ?: throw ApiException(ApiError.NotFound(hospId))
        if (current.jpaVersion != jpaVersion) {
            throw ApiException(ApiError.Conflict(hospId))
        }
        _hospitalizations.update { list ->
            list.map { hospitalization ->
                if (hospitalization.id != hospId) {
                    hospitalization
                } else {
                    hospitalization.copy(
                        status = details.type.closingStatus(),
                        endDate = clock.now(),
                        jpaVersion = hospitalization.jpaVersion + 1,
                        discharge = details,
                    )
                }
            }
        }
    }

    private fun nextId(prefix: String): String = prefix + clock.now().toEpochMilliseconds()
}

/**
 * Real [EpisodeRepository], backed by the shared `HttpClient` (HISS-632). See
 * `network/dto/EpisodeDtos.kt` for the DTOs mapped here. Not bound yet -- see
 * [InMemoryEpisodeRepository].
 *
 * Every read follows [KtorPatientRepository.getPatient]'s shape: fetch once per collection, write
 * the result into a cache, then keep observing that cache, so [addHospitalization]/[discharge]
 * reach an already-open screen without a re-fetch. Lists only ever hold `INPATIENT` episodes -- the
 * backend's episode list also returns outpatient/emergency contacts, which aren't hospital stays.
 */
class KtorEpisodeRepository(
    private val httpClient: HttpClient,
) : EpisodeRepository {

    /** Per-patient episode lists, newest first (the real endpoint's `ORDER BY startDate DESC`). */
    private val _lists = MutableStateFlow<Map<String, List<Hospitalizacion>>>(emptyMap())

    /** Per-episode cache backing [getHospitalization], kept apart from [_lists] like [KtorPatientRepository]'s `_details`. */
    private val _details = MutableStateFlow<Map<String, Hospitalizacion>>(emptyMap())

    /** An unknown [patientId] (a `404`) has no stays, so its record screen shows its own not-found state. */
    override fun getHospitalizations(patientId: String): Flow<List<Hospitalizacion>> = flow {
        val fetched = try {
            httpClient.getAllPages<EpisodeSummary>("/api/v1/patients/$patientId/episodes") {
                parameter("episodeType", EpisodeTypeDto.INPATIENT)
            }.map { it.toDomain() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.error is ApiError.NotFound) emptyList() else throw e
        }
        _lists.update { it + (patientId to fetched) }
        emitAll(_lists.map { it[patientId].orEmpty() }.distinctUntilChanged())
    }

    /**
     * The real endpoint is keyed by episode id alone, so [patientId] is checked here against the
     * response's patient header -- an episode of a different patient is not-found, never shown.
     */
    override fun getHospitalization(patientId: String, hospId: String): Flow<Hospitalizacion?> = flow {
        val fetched = try {
            httpClient.get("/api/v1/episodes/$hospId").body<EpisodeResponse>().toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.error is ApiError.NotFound) null else throw e
        }
        if (fetched == null || fetched.patientId != patientId) {
            emit(null)
            return@flow
        }
        _details.update { it + (hospId to fetched) }
        emitAll(_details.map { it[hospId] }.distinctUntilChanged())
    }

    /**
     * Opens an `INPATIENT` episode; the server stamps the start date. The backend also answers
     * `409` for an occupied bed or one outside [service] -- those carry no conflicting resource id,
     * unlike the already-open-stay conflict.
     */
    override suspend fun addHospitalization(
        patientId: String,
        service: ServiceRef,
        bed: BedRef?,
        attendingPhysician: UserRef,
        admissionReason: String?,
    ): Hospitalizacion {
        val created = httpClient.post("/api/v1/episodes") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateEpisodeRequest(
                    patientId = patientId,
                    episodeType = EpisodeTypeDto.INPATIENT,
                    attendingPhysicianId = attendingPhysician.id,
                    serviceId = service.id,
                    bedId = bed?.id,
                    admissionReason = admissionReason,
                ),
            )
        }.body<EpisodeResponse>().toDomain()
        _details.update { it + (created.id to created) }
        _lists.update { lists ->
            val list = lists[patientId] ?: return@update lists
            lists + (patientId to listOf(created) + list)
        }
        return created
    }

    /** Updates both caches from the response, so an open stay screen or list shows it closed. */
    override suspend fun discharge(hospId: String, details: DischargeDetails, jpaVersion: Long) {
        val discharged = httpClient.post("/api/v1/episodes/$hospId/discharge") {
            contentType(ContentType.Application.Json)
            setBody(details.toRequest(jpaVersion))
        }.body<EpisodeResponse>().toDomain()
        _details.update { it + (hospId to discharged) }
        _lists.update { lists ->
            lists.mapValues { (_, list) ->
                list.map { if (it.id == hospId) it.copy(status = discharged.status, endDate = discharged.endDate) else it }
            }
        }
    }
}

private fun DischargeType.closingStatus(): EpisodeStatus =
    if (this == DischargeType.TRANSFER) EpisodeStatus.TRANSFERRED else EpisodeStatus.CLOSED

/**
 * Ported 1:1 from prototype/shared/data.js's HOSPITALIZATIONS. Its dates are Lima wall-clock
 * (UTC-5) and are stored here as the equivalent instants. Their Historia Clinica and evoluciones
 * are seeded in [InMemoryClinicalNoteRepository] under the same ids.
 */
private fun seedHospitalizations(): List<Hospitalizacion> = listOf(
    Hospitalizacion(
        id = "h_ricaldi_1",
        patientId = "30c14d79-8c7e-43f8-870c-f1dbc4c90247",
        currentService = PlaceholderCatalog.service("Psiquiatria (UHSMA)"),
        currentBed = PlaceholderCatalog.bed("01"),
        attendingPhysician = PlaceholderCatalog.physician("Dr. Reyes"),
        startDate = Instant.parse("2026-05-20T19:30:00Z"),
        endDate = null,
        admissionReason = "Heteroagresividad, psicosis, adicciones",
        status = EpisodeStatus.ACTIVE,
    ),
    Hospitalizacion(
        id = "h_mendoza_1",
        patientId = "1e0697d0-f3e4-4755-85ad-d888d2b15f9d",
        currentService = PlaceholderCatalog.service("Emergencia - Topico de Medicina"),
        currentBed = PlaceholderCatalog.bed("0"),
        attendingPhysician = PlaceholderCatalog.physician("Dr. Hirsh"),
        startDate = Instant.parse("2026-06-15T17:50:00Z"),
        endDate = null,
        admissionReason = "Sintomas respiratorios",
        status = EpisodeStatus.ACTIVE,
    ),
    Hospitalizacion(
        id = "h_remon_1",
        patientId = "a21f8bfa-299c-42db-9681-c84f87a90ce4",
        currentService = PlaceholderCatalog.service("Emergencia - Topico de Medicina"),
        currentBed = PlaceholderCatalog.bed("0"),
        attendingPhysician = PlaceholderCatalog.physician("Dr. Hirsh"),
        startDate = Instant.parse("2026-06-14T14:15:00Z"),
        endDate = null,
        admissionReason = "Hematemesis",
        status = EpisodeStatus.ACTIVE,
    ),
    Hospitalizacion(
        id = "h_vasquez_1",
        patientId = "caaedede-5d72-4a52-bb72-7532d7cdda83",
        currentService = PlaceholderCatalog.service("Emergencia - Topico de Medicina"),
        currentBed = PlaceholderCatalog.bed("0"),
        attendingPhysician = PlaceholderCatalog.physician("Dr. Hirsh"),
        startDate = Instant.parse("2026-06-16T00:20:00Z"),
        endDate = null,
        admissionReason = "Insuficiencia respiratoria",
        status = EpisodeStatus.ACTIVE,
    ),
    Hospitalizacion(
        id = "h_gonzalez_1",
        patientId = "07c98942-3654-4034-b960-f3265814e214",
        currentService = PlaceholderCatalog.service("Medicina General"),
        currentBed = PlaceholderCatalog.bed("12"),
        attendingPhysician = PlaceholderCatalog.physician("Dr. Patel"),
        startDate = Instant.parse("2026-04-10T14:00:00Z"),
        endDate = Instant.parse("2026-04-12T16:30:00Z"),
        admissionReason = "Tos productiva con fiebre",
        status = EpisodeStatus.CLOSED,
    ),
    Hospitalizacion(
        id = "h_gonzalez_2",
        patientId = "07c98942-3654-4034-b960-f3265814e214",
        currentService = PlaceholderCatalog.service("Medicina General"),
        currentBed = PlaceholderCatalog.bed("08"),
        attendingPhysician = PlaceholderCatalog.physician("Dr. Patel"),
        startDate = Instant.parse("2026-01-31T13:30:00Z"),
        endDate = Instant.parse("2026-02-02T15:00:00Z"),
        admissionReason = "Control de hipertension arterial",
        status = EpisodeStatus.CLOSED,
    ),
    Hospitalizacion(
        id = "h_gonzalez_3",
        patientId = "07c98942-3654-4034-b960-f3265814e214",
        currentService = PlaceholderCatalog.service("Medicina General"),
        currentBed = PlaceholderCatalog.bed("08"),
        attendingPhysician = PlaceholderCatalog.physician("Dr. Reyes"),
        startDate = Instant.parse("2025-11-14T13:00:00Z"),
        endDate = Instant.parse("2025-11-15T14:00:00Z"),
        admissionReason = "Examen fisico anual",
        status = EpisodeStatus.CLOSED,
    ),
)
