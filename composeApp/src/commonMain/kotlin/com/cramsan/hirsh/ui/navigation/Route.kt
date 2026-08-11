package com.cramsan.hirsh.ui.navigation

// Navigation-Compose destinations, one per top-level page in the prototype's
// HTML mocks. Add one per page as it's built out, following the same names
// where they line up (patients, record, admision, historia-clinica,
// evolucion, accounts...).
//
// Plain string routes rather than the newer @Serializable type-safe routes:
// the type-safe API hits a kotlinx.serialization/K2 compiler crash on the
// wasmJs target as of Kotlin 2.1.21 (NPE in generateSerializerImplClass,
// reproduces even with a single top-level @Serializable object/data class,
// JVM-only -- not present when compiling the jvm target). Worth revisiting
// once that's fixed upstream.
object Routes {
    const val LOGIN = "login"
    const val PATIENTS = "patients"
    const val PROFILE = "profile"
    const val PATIENT_RECORD = "patient_record/{patientId}"
    const val PATIENT_REGISTER = "patient_register"
    const val PATIENT_EDIT = "patient_edit/{patientId}"
    const val PATIENT_HISTORY = "patient_history/{patientId}"
    const val ADMISION = "admision/{patientId}"
    const val HOSPITALIZATION = "hospitalization/{patientId}/{hospId}"
    const val HISTORIA_CLINICA = "historia_clinica/{patientId}/{hospId}"
    const val EVOLUCION_NEW = "evolucion_new/{patientId}/{hospId}"
    const val EVOLUCION_VIEW = "evolucion_view/{patientId}/{hospId}/{evoId}"
    const val ACCOUNTS = "accounts"

    fun patientRecord(patientId: String) = "patient_record/${encodeRouteSegment(patientId)}"
    fun patientEdit(patientId: String) = "patient_edit/${encodeRouteSegment(patientId)}"
    fun patientHistory(patientId: String) = "patient_history/${encodeRouteSegment(patientId)}"
    fun admision(patientId: String) = "admision/${encodeRouteSegment(patientId)}"
    fun hospitalization(patientId: String, hospId: String) =
        "hospitalization/${encodeRouteSegment(patientId)}/${encodeRouteSegment(hospId)}"
    fun historiaClinica(patientId: String, hospId: String) =
        "historia_clinica/${encodeRouteSegment(patientId)}/${encodeRouteSegment(hospId)}"
    fun evolucionNew(patientId: String, hospId: String) =
        "evolucion_new/${encodeRouteSegment(patientId)}/${encodeRouteSegment(hospId)}"
    fun evolucionView(patientId: String, hospId: String, evoId: String) =
        "evolucion_view/${encodeRouteSegment(patientId)}/${encodeRouteSegment(hospId)}/${encodeRouteSegment(evoId)}"
}

/**
 * Navigation-Compose matches even plain string routes via URI templating under the hood, so a
 * reserved URI character in a path segment breaks route matching -- concretely, every seeded
 * [com.cramsan.hirsh.model.Patient.id] is formatted like `#00142` (matching the prototype's own
 * display convention), and an un-encoded `#` truncates the rest of the segment as a URI fragment,
 * leaving `{patientId}` empty and every patient-scoped screen unreachable. Percent-encode any
 * character outside the URI-unreserved set when building a route segment; [decodeRouteSegment]
 * reverses it when reading the argument back out in AppNavHost.
 */
private fun encodeRouteSegment(value: String): String = buildString {
    for (char in value) {
        if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' || char == '~') {
            append(char)
        } else {
            append('%')
            append(char.code.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

/** Reverses [encodeRouteSegment] -- ASCII-only, matching this app's actual id space (no seeded id contains non-ASCII). */
fun decodeRouteSegment(value: String): String = buildString {
    var i = 0
    while (i < value.length) {
        val char = value[i]
        if (char == '%' && i + 2 < value.length) {
            append(value.substring(i + 1, i + 3).toInt(16).toChar())
            i += 3
        } else {
            append(char)
            i++
        }
    }
}
