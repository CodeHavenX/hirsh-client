package com.cramsan.hirsh.network.dto

import com.cramsan.hirsh.model.AppliedScale
import com.cramsan.hirsh.model.SUICIDE_RISK_ALERT
import com.cramsan.hirsh.model.ScaleDefinition
import com.cramsan.hirsh.model.ScaleOption
import com.cramsan.hirsh.model.ScaleQuestion
import com.cramsan.hirsh.model.ScaleScore
import com.cramsan.hirsh.model.ScaleSeverity
import com.cramsan.hirsh.model.ScaleSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

/** One entry of `GET /api/v1/test-templates` -- schema `TestTemplateCard`. */
@Serializable
data class TestTemplateCard(
    val id: String,
    val code: String,
    val name: String,
    val description: String? = null,
    val active: Boolean = true,
)

/** 200 body of `GET /api/v1/test-templates/{id}` -- schema `TestTemplateResponse`. Scoring rules stay on the server. */
@Serializable
data class TestTemplateResponse(
    val id: String,
    val code: String,
    val name: String,
    val jsonStructure: JsonObject,
    val instructions: String? = null,
)

/** Schema `TestTemplateSummary`. */
@Serializable
data class TestTemplateSummary(val id: String, val code: String, val name: String)

/** Body of `POST /api/v1/test-templates/{id}/score-preview` -- schema `ScorePreviewRequest`. */
@Serializable
data class ScorePreviewRequest(val answersJson: Map<String, Int>)

/** 200 body of the score preview -- schema `ScorePreviewResponse`. */
@Serializable
data class ScorePreviewResponse(
    val totalScore: Double,
    val severity: String? = null,
    val interpretation: String? = null,
    val wouldTriggerAlerts: List<String> = emptyList(),
)

/** Body of `POST /api/v1/clinical-documents/{documentId}/applied-tests` -- schema `ApplyTestRequest`. `appliedAt` is left to the server's clock. */
@Serializable
data class ApplyTestRequest(
    val testTemplateId: String,
    val answersJson: Map<String, Int>,
    val clinicalObservations: String? = null,
)

/** Schema `AppliedTestResponse`. Answers and the rules snapshot aren't mapped: nothing here shows them. */
@Serializable
data class AppliedTestResponse(
    val id: String,
    val clinicalDocumentId: String,
    val testTemplate: TestTemplateSummary,
    val appliedAt: String,
    val appliedBy: UserRefDto,
    val totalScore: Double? = null,
    val severity: String? = null,
    val interpretation: String? = null,
    val suicideAlertTriggered: Boolean,
    val clinicalObservations: String? = null,
)

fun TestTemplateCard.toDomain(): ScaleSummary = ScaleSummary(id = id, code = code, name = name, description = description)

/**
 * The backend checks answers against every node of `jsonStructure` with an `id` and an `options`
 * list (hirsh#19's AnswerValidator), so the client reads its items the same way, wherever they nest.
 */
fun TestTemplateResponse.toDomain(): ScaleDefinition = ScaleDefinition(
    id = id,
    code = code,
    name = name,
    stem = (jsonStructure["stem"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
    instructions = instructions,
    questions = buildList { collectQuestions(jsonStructure, this) },
)

private fun collectQuestions(node: JsonElement, into: MutableList<ScaleQuestion>) {
    when (node) {
        is JsonObject -> {
            val id = (node["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val options = (node["options"] as? JsonArray).orEmpty().mapNotNull { it.toOption() }
            if (id != null && options.isNotEmpty()) {
                val text = listOf("text", "label").firstNotNullOfOrNull { (node[it] as? JsonPrimitive)?.content } ?: id
                into += ScaleQuestion(id = id, text = text, options = options)
            }
            node.values.forEach { collectQuestions(it, into) }
        }
        is JsonArray -> node.forEach { collectQuestions(it, into) }
        else -> Unit
    }
}

private fun JsonElement.toOption(): ScaleOption? {
    val option = this as? JsonObject ?: return null
    val value = option["value"]?.jsonPrimitive?.doubleOrNull ?: return null
    val label = (option["label"] as? JsonPrimitive)?.content ?: value.toInt().toString()
    return ScaleOption(value = value.toInt(), label = label)
}

fun ScorePreviewResponse.toDomain(): ScaleScore = ScaleScore(
    total = totalScore,
    severity = severity.toScaleSeverity(),
    interpretation = interpretation,
    raisesSuicideAlert = SUICIDE_RISK_ALERT in wouldTriggerAlerts,
)

fun AppliedTestResponse.toDomain(): AppliedScale = AppliedScale(
    id = id,
    documentId = clinicalDocumentId,
    scaleCode = testTemplate.code,
    scaleName = testTemplate.name,
    appliedAt = Instant.parse(appliedAt),
    appliedBy = appliedBy.fullName,
    score = ScaleScore(
        total = totalScore ?: 0.0,
        severity = severity.toScaleSeverity(),
        interpretation = interpretation,
        raisesSuicideAlert = suicideAlertTriggered,
    ),
    observations = clinicalObservations,
)

/** Lenient: a band added on the server later shows without a badge rather than failing the read. */
private fun String?.toScaleSeverity(): ScaleSeverity? = ScaleSeverity.entries.firstOrNull { it.name == this }
