package com.cramsan.hirsh.model

/**
 * Where a clinical note stands in the backend's document lifecycle (its `DocumentStatus`, HCEM-79).
 * A [DRAFT] is still editable and waits for a signature; a [SIGNED] note is frozen; a [VOIDED] one
 * was withdrawn from the record but stays readable.
 */
enum class NoteStatus { DRAFT, SIGNED, VOIDED }

fun NoteStatus.toDisplayLabel(): String = when (this) {
    NoteStatus.DRAFT -> "Pendiente de firma"
    NoteStatus.SIGNED -> "Firmada"
    NoteStatus.VOIDED -> "Anulada"
}
