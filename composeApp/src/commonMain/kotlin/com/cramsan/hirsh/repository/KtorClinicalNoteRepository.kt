package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.dto.AutosaveDraftRequest
import com.cramsan.hirsh.network.dto.AutosaveDraftResponse
import com.cramsan.hirsh.network.dto.CreateDocumentRequest
import com.cramsan.hirsh.network.dto.DiagnosticoContent
import com.cramsan.hirsh.network.dto.DocumentCategoryDto
import com.cramsan.hirsh.network.dto.DocumentDetail
import com.cramsan.hirsh.network.dto.DocumentFolderEntry
import com.cramsan.hirsh.network.dto.DocumentStatusDto
import com.cramsan.hirsh.network.dto.EnfermedadActualContent
import com.cramsan.hirsh.network.dto.EpisodeTypeDto
import com.cramsan.hirsh.network.dto.EvolucionContent
import com.cramsan.hirsh.network.dto.ExamenFisicoContent
import com.cramsan.hirsh.network.dto.FiliacionContent
import com.cramsan.hirsh.network.dto.MotivoIngresoContent
import com.cramsan.hirsh.network.dto.PlanContent
import com.cramsan.hirsh.network.dto.SignDraftRequest
import com.cramsan.hirsh.network.dto.TemplateCard
import com.cramsan.hirsh.network.dto.pronosticoOrDefault
import com.cramsan.hirsh.network.dto.resultadoOrDefault
import com.cramsan.hirsh.network.dto.toContent
import com.cramsan.hirsh.network.dto.toDomain
import com.cramsan.hirsh.network.getAllPages
import com.cramsan.hirsh.util.formatDate
import com.cramsan.hirsh.util.formatTime
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Instant

/** The templates seeded by hirsh#17. Looked up by code, never by id: publishing a new version changes the id. */
internal const val HC_TEMPLATE_CODE = "HC_PSIQ"
internal const val EVOLUCION_TEMPLATE_CODE = "EVOL_PSIQ"

/**
 * Defaults are encoded so a section saved with every field blank or unchecked still writes each
 * key -- the stored content then always has the template's full shape.
 */
private val contentJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** A document as last read or written, with what the next write to it needs. */
private data class NoteDocument(
    val documentId: String,
    val episodeId: String,
    val category: DocumentCategoryDto,
    val status: NoteStatus,
    val documentDate: Instant,
    val author: String,
    val versionId: String,
    val jpaVersion: Long,
    val draftDeadline: Instant?,
    val content: JsonObject,
)

/**
 * Real [ClinicalNoteRepository] over the backend's clinical documents (HCEM-79, mapping in
 * HCEM-74): the stay's Historia Clinica is its one ADMISSION document, each evolucion a
 * PROGRESS_NOTE.
 *
 * Reads follow [KtorEpisodeRepository]'s shape -- fetch once per collection into a cache, then keep
 * observing the cache -- so a save, sign or new evolucion reaches every open screen without a
 * re-fetch. Writes are serialized through [writeLock]: each one sends the lock token the previous
 * one returned, so two saves from this client never race each other into a `409`.
 */
class KtorClinicalNoteRepository(
    private val httpClient: HttpClient,
) : ClinicalNoteRepository {

    /** Per stay; a present key with a null value means "loaded, and the stay has no Historia Clinica yet". */
    private val _historias = MutableStateFlow<Map<String, NoteDocument?>>(emptyMap())

    /** Per stay, newest first. */
    private val _evoluciones = MutableStateFlow<Map<String, List<NoteDocument>>>(emptyMap())

    /** Per evolucion, backing [getEvolucion] apart from the lists like [KtorEpisodeRepository]'s `_details`. */
    private val _evolucionDetails = MutableStateFlow<Map<String, NoteDocument>>(emptyMap())

    private val templateIds = mutableMapOf<String, String>()
    private val writeLock = Mutex()

    private val _signatures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val signatures: Flow<Unit> = _signatures.asSharedFlow()

    override fun getHistoriaClinica(hospId: String): Flow<HistoriaClinica> = flow {
        refreshHistoriaClinica(hospId)
        emitAll(_historias.map { it[hospId].toHistoriaClinica() }.distinctUntilChanged())
    }

    override fun getEvoluciones(hospId: String): Flow<List<Evolucion>> = flow {
        val documents = coroutineScope {
            folder(hospId, DocumentCategoryDto.PROGRESS_NOTE)
                .map { entry -> async { fetchDocument(entry.id) } }
                .awaitAll()
        }.sortedByDescending { it.documentDate }
        _evoluciones.update { it + (hospId to documents) }
        emitAll(_evoluciones.map { lists -> lists[hospId].orEmpty().map { it.toEvolucion() } }.distinctUntilChanged())
    }

    /** Not-found unless [evoId] is an evolucion of [hospId] -- a note of another stay is never shown. */
    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> = flow {
        val document = fetchDocumentOrNull(evoId)
        if (document == null || !document.isEvolucionOf(hospId)) {
            emit(null)
            return@flow
        }
        _evolucionDetails.update { it + (evoId to document) }
        emitAll(_evolucionDetails.map { it[evoId]?.toEvolucion() }.distinctUntilChanged())
    }

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) {
        require(key.implemented) { "HistoriaClinica section $key has no digital form yet" }
        val section = encodeSection(key, data)
        writeLock.withLock {
            val current = cachedHistoriaClinica(hospId)
            if (current == null) {
                val created = reloadingOnConflict(hospId) {
                    createDocument(hospId, HC_TEMPLATE_CODE, DocumentCategoryDto.ADMISSION, buildJsonObject { put(key.contentKey, section) })
                }
                _historias.update { it + (hospId to created) }
                return
            }
            if (current.status != NoteStatus.DRAFT) throw ApiException(ApiError.Conflict(current.documentId))
            val content = JsonObject(current.content + (key.contentKey to section))
            val saved = reloadingOnConflict(hospId) { saveDraft(current.versionId, content, current.jpaVersion) }
            _historias.update { it + (hospId to current.copy(content = content, jpaVersion = saved.jpaVersion)) }
        }
    }

    override suspend fun signHistoriaClinica(hospId: String) {
        writeLock.withLock {
            val current = cachedHistoriaClinica(hospId) ?: throw ApiException(ApiError.NotFound(hospId))
            if (current.status != NoteStatus.DRAFT) throw ApiException(ApiError.Conflict(current.documentId))
            reloadingOnConflict(hospId) { sign(current.versionId, current.jpaVersion) }
            _signatures.tryEmit(Unit)
            refreshHistoriaClinica(hospId)
        }
    }

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion = writeLock.withLock {
        val created = createDocument(
            hospId,
            EVOLUCION_TEMPLATE_CODE,
            DocumentCategoryDto.PROGRESS_NOTE,
            contentJson.encodeToJsonElement(evolucion.toContent()) as JsonObject,
        )
        _evolucionDetails.update { it + (created.documentId to created) }
        _evoluciones.update { lists ->
            val list = lists[hospId] ?: return@update lists
            lists + (hospId to listOf(created) + list)
        }
        created.toEvolucion()
    }

    override suspend fun signEvolucion(hospId: String, evoId: String) {
        writeLock.withLock {
            val current = _evolucionDetails.value[evoId]
                ?: _evoluciones.value[hospId]?.find { it.documentId == evoId }
                ?: fetchDocumentOrNull(evoId)
            if (current == null || !current.isEvolucionOf(hospId)) throw ApiException(ApiError.NotFound(evoId))
            if (current.status != NoteStatus.DRAFT) throw ApiException(ApiError.Conflict(evoId))
            try {
                sign(current.versionId, current.jpaVersion)
            } catch (e: ApiException) {
                if (e.error is ApiError.Conflict) cacheEvolucion(hospId, fetchDocument(evoId))
                throw e
            }
            _signatures.tryEmit(Unit)
            cacheEvolucion(hospId, fetchDocument(evoId))
        }
    }

    override suspend fun latestVersionId(hospId: String, evoId: String?): String? =
        if (evoId == null) {
            refreshHistoriaClinica(hospId)?.versionId
        } else {
            fetchDocumentOrNull(evoId)?.takeIf { it.isEvolucionOf(hospId) }?.versionId
        }

    private suspend fun refreshHistoriaClinica(hospId: String): NoteDocument? {
        val entry = folder(hospId, DocumentCategoryDto.ADMISSION).firstOrNull()
        val document = entry?.let { fetchDocument(it.id) }
        _historias.update { it + (hospId to document) }
        return document
    }

    private suspend fun cachedHistoriaClinica(hospId: String): NoteDocument? =
        if (hospId in _historias.value) _historias.value[hospId] else refreshHistoriaClinica(hospId)

    /**
     * A `409` means the document moved on (someone else saved or signed) or a Historia Clinica
     * already exists. Re-reading before rethrowing puts the newer state on every open screen, so
     * the user decides what to do with it instead of this repository writing over it.
     */
    private suspend fun <T> reloadingOnConflict(hospId: String, write: suspend () -> T): T = try {
        write()
    } catch (e: ApiException) {
        if (e.error is ApiError.Conflict) refreshHistoriaClinica(hospId)
        throw e
    }

    private fun cacheEvolucion(hospId: String, document: NoteDocument) {
        _evolucionDetails.update { it + (document.documentId to document) }
        _evoluciones.update { lists ->
            val list = lists[hospId] ?: return@update lists
            lists + (hospId to list.map { if (it.documentId == document.documentId) document else it })
        }
    }

    /** Non-voided documents of [category] in the stay. An unknown stay (a `404`) has none. */
    private suspend fun folder(hospId: String, category: DocumentCategoryDto): List<DocumentFolderEntry> = try {
        httpClient.getAllPages<DocumentFolderEntry>("/api/v1/episodes/$hospId/documents") {
            parameter("category", category)
        }.filter { it.documentStatus != DocumentStatusDto.VOIDED }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        if (e.error is ApiError.NotFound) emptyList() else throw e
    }

    private suspend fun fetchDocument(documentId: String): NoteDocument =
        httpClient.get("/api/v1/clinical-documents/$documentId").body<DocumentDetail>().toNoteDocument()

    private suspend fun fetchDocumentOrNull(documentId: String): NoteDocument? = try {
        fetchDocument(documentId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        if (e.error is ApiError.NotFound) null else throw e
    }

    private suspend fun createDocument(
        hospId: String,
        templateCode: String,
        category: DocumentCategoryDto,
        content: JsonObject,
    ): NoteDocument = httpClient.post("/api/v1/episodes/$hospId/documents") {
        contentType(ContentType.Application.Json)
        setBody(CreateDocumentRequest(templateId(templateCode, category), content))
    }.body<DocumentDetail>().toNoteDocument()

    private suspend fun saveDraft(versionId: String, content: JsonObject, jpaVersion: Long): AutosaveDraftResponse =
        httpClient.put("/api/v1/document-versions/$versionId/draft") {
            contentType(ContentType.Application.Json)
            setBody(AutosaveDraftRequest(content, jpaVersion))
        }.body()

    private suspend fun sign(versionId: String, jpaVersion: Long) {
        httpClient.post("/api/v1/document-versions/$versionId/sign") {
            contentType(ContentType.Application.Json)
            setBody(SignDraftRequest(jpaVersion))
        }
    }

    private suspend fun templateId(code: String, category: DocumentCategoryDto): String {
        templateIds[code]?.let { return it }
        val id = httpClient.getAllPages<TemplateCard>("/api/v1/document-templates") {
            parameter("category", category)
            parameter("episodeType", EpisodeTypeDto.INPATIENT)
        }.find { it.code == code }?.id
            ?: throw IllegalStateException("Document template $code isn't published on the backend")
        templateIds[code] = id
        return id
    }
}

private fun NoteDocument.isEvolucionOf(hospId: String): Boolean =
    episodeId == hospId && category == DocumentCategoryDto.PROGRESS_NOTE

private fun DocumentDetail.toNoteDocument() = NoteDocument(
    documentId = id,
    episodeId = episodeId,
    category = template.category,
    status = documentStatus.toDomain(),
    documentDate = Instant.parse(documentDate),
    author = versionHistory.minByOrNull { it.versionNumber }?.createdBy?.fullName.orEmpty(),
    versionId = latestVersion.id,
    jpaVersion = latestVersion.audit.jpaVersion,
    draftDeadline = latestVersion.draftDeadline?.let(Instant::parse),
    content = latestVersion.content,
)

private fun NoteDocument?.toHistoriaClinica(): HistoriaClinica {
    if (this == null) return HistoriaClinica()
    return HistoriaClinica(
        filiacion = content.section<FiliacionContent, Filiacion>(HcSectionKey.FILIACION) { it.toDomain() },
        motivoIngreso = content.section<MotivoIngresoContent, MotivoIngreso>(HcSectionKey.MOTIVO_INGRESO) { it.toDomain() },
        enfermedadActual = content.section<EnfermedadActualContent, EnfermedadActual>(HcSectionKey.ENFERMEDAD_ACTUAL) { it.toDomain() },
        examenFisico = content.section<ExamenFisicoContent, ExamenFisico>(HcSectionKey.EXAMEN_FISICO) { it.toDomain() },
        diagnostico = content.section<DiagnosticoContent, Diagnostico>(HcSectionKey.DIAGNOSTICO) { it.toDomain() },
        plan = content.section<PlanContent, Plan>(HcSectionKey.PLAN) { it.toDomain() },
        status = status,
        draftDeadline = draftDeadline.takeIf { status == NoteStatus.DRAFT },
    )
}

/** A section is complete exactly when its key is in the content (HCEM-74). */
private inline fun <reified C, D> JsonObject.section(key: HcSectionKey, toDomain: (C) -> D): HcSection<D> =
    get(key.contentKey)?.let { HcSection(complete = true, data = toDomain(contentJson.decodeFromJsonElement<C>(it))) }
        ?: HcSection()

private fun encodeSection(key: HcSectionKey, data: Any): JsonElement {
    fun wrongType(): Nothing = throw IllegalArgumentException("$key needs its own section type, got ${data::class.simpleName}")
    return when (key) {
        HcSectionKey.FILIACION -> contentJson.encodeToJsonElement((data as? Filiacion ?: wrongType()).toContent())
        HcSectionKey.MOTIVO_INGRESO -> contentJson.encodeToJsonElement((data as? MotivoIngreso ?: wrongType()).toContent())
        HcSectionKey.ENFERMEDAD_ACTUAL -> contentJson.encodeToJsonElement((data as? EnfermedadActual ?: wrongType()).toContent())
        HcSectionKey.EXAMEN_FISICO -> contentJson.encodeToJsonElement((data as? ExamenFisico ?: wrongType()).toContent())
        HcSectionKey.DIAGNOSTICO -> contentJson.encodeToJsonElement((data as? Diagnostico ?: wrongType()).toContent())
        HcSectionKey.PLAN -> contentJson.encodeToJsonElement((data as? Plan ?: wrongType()).toContent())
        HcSectionKey.ANTECEDENTES,
        HcSectionKey.HISTORIA_PERSONAL,
        HcSectionKey.INVENTARIO_SINTOMAS,
        HcSectionKey.EXAMEN_PSICOPATOLOGICO,
        -> error("unreachable: guarded by require(key.implemented) in saveHistoriaClinicaSection")
    }
}

/** The HC_PSIQ template's section keys. */
private val HcSectionKey.contentKey: String
    get() = when (this) {
        HcSectionKey.FILIACION -> "filiacion"
        HcSectionKey.MOTIVO_INGRESO -> "motivoIngreso"
        HcSectionKey.ENFERMEDAD_ACTUAL -> "enfermedadActual"
        HcSectionKey.ANTECEDENTES -> "antecedentes"
        HcSectionKey.HISTORIA_PERSONAL -> "historiaPersonal"
        HcSectionKey.EXAMEN_FISICO -> "examenFisico"
        HcSectionKey.INVENTARIO_SINTOMAS -> "inventarioSintomas"
        HcSectionKey.EXAMEN_PSICOPATOLOGICO -> "examenPsicopatologico"
        HcSectionKey.DIAGNOSTICO -> "diagnostico"
        HcSectionKey.PLAN -> "plan"
    }

private fun Evolucion.toContent() = EvolucionContent(
    vitals = vitals.toContent(),
    diagnosticos = diagnosticos.map { it.toContent() },
    subjective = subjective,
    objective = objective,
    assessment = assessment,
    plan = plan,
    rx = rx,
    pronostico = pronostico.name,
    resultado = resultado.name,
    examenes = examenes.map { it.toContent() },
    examenesObs = examenesObs,
    prescriptions = prescripciones.map { it.toContent() },
)

/** [Evolucion.fecha]/[Evolucion.hora] are the document's clinical date in local time; [Evolucion.medico] is who wrote version 1. */
private fun NoteDocument.toEvolucion(): Evolucion {
    val evolucion = contentJson.decodeFromJsonElement<EvolucionContent>(content)
    val timeZone = TimeZone.currentSystemDefault()
    return Evolucion(
        id = documentId,
        fecha = formatDate(documentDate, timeZone),
        hora = formatTime(documentDate.toLocalDateTime(timeZone).time),
        medico = author,
        vitals = evolucion.vitals.toDomain(),
        diagnosticos = evolucion.diagnosticos.map { it.toDomain() },
        subjective = evolucion.subjective,
        objective = evolucion.objective,
        assessment = evolucion.assessment,
        plan = evolucion.plan,
        rx = evolucion.rx,
        pronostico = evolucion.pronosticoOrDefault(),
        resultado = evolucion.resultadoOrDefault(),
        examenes = evolucion.examenes.map { it.toDomain() },
        examenesObs = evolucion.examenesObs,
        status = status,
        prescripciones = evolucion.prescriptions.map { it.toDomain() },
    )
}
