package com.cramsan.hirsh.model

import kotlin.time.Instant

/**
 * A psychometric scale clinicians apply inside a clinical document (PHQ-9...). The backend calls
 * these test templates; it scores the answers itself, so the client never computes a score.
 */
data class ScaleSummary(
    val id: String,
    val code: String,
    val name: String,
    val description: String?,
)

/** A scale's items, as published. */
data class ScaleDefinition(
    val id: String,
    val code: String,
    val name: String,
    /** The lead-in read before every item, e.g. "Durante las ultimas 2 semanas...". */
    val stem: String?,
    val instructions: String?,
    val questions: List<ScaleQuestion>,
)

data class ScaleQuestion(
    val id: String,
    val text: String,
    val options: List<ScaleOption>,
)

data class ScaleOption(val value: Int, val label: String)

enum class ScaleSeverity { MINIMAL, MILD, MODERATE, MOD_SEVERE, SEVERE }

/** The backend's score for a set of answers, saved or not. */
data class ScaleScore(
    val total: Double,
    val severity: ScaleSeverity?,
    /** The band's own label, e.g. "Depresion moderada". */
    val interpretation: String?,
    val raisesSuicideAlert: Boolean,
)

/** A scale applied during a stay, attached to the clinical document (an evolucion) it was applied in. */
data class AppliedScale(
    val id: String,
    val documentId: String,
    val scaleCode: String,
    val scaleName: String,
    val appliedAt: Instant,
    val appliedBy: String,
    val score: ScaleScore,
    val observations: String?,
)

/** The alert the backend raises, and notifies the responsible clinicians of, on a suicide-risk answer. */
const val SUICIDE_RISK_ALERT = "SUICIDE_RISK"

fun ScaleSeverity.toDisplayLabel(): String = when (this) {
    ScaleSeverity.MINIMAL -> "Minima"
    ScaleSeverity.MILD -> "Leve"
    ScaleSeverity.MODERATE -> "Moderada"
    ScaleSeverity.MOD_SEVERE -> "Moderadamente severa"
    ScaleSeverity.SEVERE -> "Severa"
}

/** Scores are whole numbers for every scale so far; the backend sends them with two decimals. */
fun ScaleScore.totalLabel(): String = if (total % 1.0 == 0.0) total.toLong().toString() else total.toString()
