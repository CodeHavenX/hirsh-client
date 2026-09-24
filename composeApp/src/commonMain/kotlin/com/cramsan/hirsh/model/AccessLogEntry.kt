package com.cramsan.hirsh.model

/**
 * One entry of a patient's access trail (`GET /api/v1/patients/{patientId}/access-logs`, HISS-624)
 * -- written automatically by the backend's audit filter on every request touching the patient.
 * It only records who/when/what kind of action: no field-level before/after values exist server
 * side, which is why this replaced the prototype's field-diff change log.
 *
 * [userId] is the acting account's id; resolving it to a name needs `USER_MANAGE` and a real
 * account repository, so for now it's shown shortened (see [shortUserId]).
 */
data class AccessLogEntry(
    val id: String,
    val userId: String?,
    val action: AccessAction,
    /** Already formatted for display in the local time zone, e.g. `24 Sep 2026 · 11:05`. */
    val occurredAt: String,
    val sourceIp: String?,
)

/**
 * Mirrors the backend's `AccessLogAction`. [OTHER] absorbs any value this client doesn't know yet,
 * so a new server-side action can't break decoding the whole trail.
 */
enum class AccessAction {
    LOGIN,
    LOGOUT,
    RECORD_READ,
    DOCUMENT_CREATED,
    DOCUMENT_SIGNED,
    DOCUMENT_VOIDED,
    PDF_EXPORTED,
    TEST_ACCESSED,
    PATIENT_MODIFIED,
    FILE_ACCESS,
    PRINT,
    OTHER,
}

fun AccessAction.toDisplayLabel(): String = when (this) {
    AccessAction.LOGIN -> "Inicio de sesion"
    AccessAction.LOGOUT -> "Cierre de sesion"
    AccessAction.RECORD_READ -> "Consulta del registro"
    AccessAction.DOCUMENT_CREATED -> "Documento creado"
    AccessAction.DOCUMENT_SIGNED -> "Documento firmado"
    AccessAction.DOCUMENT_VOIDED -> "Documento anulado"
    AccessAction.PDF_EXPORTED -> "Exportacion PDF"
    AccessAction.TEST_ACCESSED -> "Consulta de prueba"
    AccessAction.PATIENT_MODIFIED -> "Modificacion de datos"
    AccessAction.FILE_ACCESS -> "Acceso a adjunto"
    AccessAction.PRINT -> "Impresion"
    AccessAction.OTHER -> "Otra accion"
}

/** The first 8 characters of [userId] -- enough to tell accounts apart until names can be resolved. */
fun AccessLogEntry.shortUserId(): String = userId?.take(8) ?: "—"

/** One page of the trail, newest first, plus how many entries exist in total. */
data class AccessLogPage(val entries: List<AccessLogEntry>, val totalEntries: Long)
