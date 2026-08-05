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

    fun patientRecord(patientId: String) = "patient_record/$patientId"
    fun patientEdit(patientId: String) = "patient_edit/$patientId"
    fun patientHistory(patientId: String) = "patient_history/$patientId"
    fun admision(patientId: String) = "admision/$patientId"
    fun hospitalization(patientId: String, hospId: String) = "hospitalization/$patientId/$hospId"
    fun historiaClinica(patientId: String, hospId: String) = "historia_clinica/$patientId/$hospId"
    fun evolucionNew(patientId: String, hospId: String) = "evolucion_new/$patientId/$hospId"
    fun evolucionView(patientId: String, hospId: String, evoId: String) =
        "evolucion_view/$patientId/$hospId/$evoId"
}
