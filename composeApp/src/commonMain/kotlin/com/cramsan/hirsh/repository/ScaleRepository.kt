package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.AppliedScale
import com.cramsan.hirsh.model.ScaleDefinition
import com.cramsan.hirsh.model.ScaleScore
import com.cramsan.hirsh.model.ScaleSummary
import com.cramsan.hirsh.network.dto.AppliedTestResponse
import com.cramsan.hirsh.network.dto.ApplyTestRequest
import com.cramsan.hirsh.network.dto.ScorePreviewRequest
import com.cramsan.hirsh.network.dto.ScorePreviewResponse
import com.cramsan.hirsh.network.dto.TestTemplateCard
import com.cramsan.hirsh.network.dto.TestTemplateResponse
import com.cramsan.hirsh.network.dto.toDomain
import com.cramsan.hirsh.network.getAllPages
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Psychometric scales (HCEM-80): what can be applied, its live score, and what a stay already has. */
interface ScaleRepository {
    /** The latest active version of each scale. */
    suspend fun getScales(): List<ScaleSummary>

    suspend fun getScale(scaleId: String): ScaleDefinition

    /** Scores [answers] (item id to option value) without saving; a partly answered scale is fine. */
    suspend fun previewScore(scaleId: String, answers: Map<String, Int>): ScaleScore

    /** Applies the scale inside the clinical document [documentId] (an evolucion) of the stay [hospId]. Every item must be answered. */
    suspend fun applyScale(
        hospId: String,
        documentId: String,
        scaleId: String,
        answers: Map<String, Int>,
        observations: String?,
    ): AppliedScale

    /** The stay's applied scales, newest first. Includes ones applied through [applyScale] since. */
    fun getAppliedScales(hospId: String): Flow<List<AppliedScale>>
}

/**
 * Real [ScaleRepository] over the backend's test templates and applied tests. Applied scales follow
 * [KtorClinicalNoteRepository]'s shape -- fetch once per collection into a cache, then keep observing
 * it -- so a scale applied from an evolucion shows on the stay's screen without a re-fetch.
 */
class KtorScaleRepository(
    private val httpClient: HttpClient,
) : ScaleRepository {

    /** Per stay, newest first. */
    private val _applied = MutableStateFlow<Map<String, List<AppliedScale>>>(emptyMap())

    override suspend fun getScales(): List<ScaleSummary> =
        httpClient.getAllPages<TestTemplateCard>("/api/v1/test-templates")
            .filter { it.active }
            .map { it.toDomain() }

    override suspend fun getScale(scaleId: String): ScaleDefinition =
        httpClient.get("/api/v1/test-templates/$scaleId").body<TestTemplateResponse>().toDomain()

    override suspend fun previewScore(scaleId: String, answers: Map<String, Int>): ScaleScore =
        httpClient.post("/api/v1/test-templates/$scaleId/score-preview") {
            contentType(ContentType.Application.Json)
            setBody(ScorePreviewRequest(answers))
        }.body<ScorePreviewResponse>().toDomain()

    override suspend fun applyScale(
        hospId: String,
        documentId: String,
        scaleId: String,
        answers: Map<String, Int>,
        observations: String?,
    ): AppliedScale {
        val applied = httpClient.post("/api/v1/clinical-documents/$documentId/applied-tests") {
            contentType(ContentType.Application.Json)
            setBody(ApplyTestRequest(scaleId, answers, observations?.takeIf { it.isNotBlank() }))
        }.body<AppliedTestResponse>().toDomain()
        _applied.update { cached ->
            cached[hospId]?.let { cached + (hospId to (listOf(applied) + it)) } ?: cached
        }
        return applied
    }

    override fun getAppliedScales(hospId: String): Flow<List<AppliedScale>> = flow {
        val applied = httpClient.getAllPages<AppliedTestResponse>("/api/v1/episodes/$hospId/applied-tests")
            .map { it.toDomain() }
            .sortedByDescending { it.appliedAt }
        _applied.update { it + (hospId to applied) }
        emitAll(_applied.map { it[hospId].orEmpty() }.distinctUntilChanged())
    }
}
