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

    fun patientRecord(patientId: String) = "patient_record/$patientId"
}
