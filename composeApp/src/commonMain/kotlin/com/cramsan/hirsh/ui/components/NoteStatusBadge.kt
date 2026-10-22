package com.cramsan.hirsh.ui.components

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.util.formatDateTime
import kotlin.time.Instant

fun NoteStatus.toBadgeTone(): BadgeTone = when (this) {
    NoteStatus.DRAFT -> BadgeTone.Warn
    NoteStatus.SIGNED -> BadgeTone.Done
    NoteStatus.VOIDED -> BadgeTone.Off
}

/**
 * Whether a clinical note is signed (HCEM-79), shared by the Historia Clinica, the evolucion view
 * and the hospitalization's note list. A draft shows its [draftDeadline] when it has one.
 */
@Composable
fun NoteStatusBadge(status: NoteStatus, draftDeadline: Instant? = null) {
    val deadline = draftDeadline?.takeIf { status == NoteStatus.DRAFT }?.let { " · vence ${formatDateTime(it)}" }.orEmpty()
    StatusBadge(text = status.toDisplayLabel() + deadline, tone = status.toBadgeTone())
}
