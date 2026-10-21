package com.cramsan.hirsh.ui.components

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.EpisodeStatus
import com.cramsan.hirsh.model.toDisplayLabel

fun EpisodeStatus.toBadgeTone(): BadgeTone = when (this) {
    EpisodeStatus.ACTIVE -> BadgeTone.Progress
    EpisodeStatus.CLOSED -> BadgeTone.Done
    EpisodeStatus.TRANSFERRED -> BadgeTone.Neutral
}

/** Status badge shared by the patient record's visit cards and the hospitalization header. */
@Composable
fun EpisodeStatusBadge(status: EpisodeStatus) {
    StatusBadge(text = status.toDisplayLabel(), tone = status.toBadgeTone())
}
