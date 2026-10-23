package com.cramsan.hirsh.ui.screens.evolucionview

import com.cramsan.hirsh.model.treatmentText
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.Patient

private const val SIS_RULE = "========================================"

/**
 * The evolucion as plain text for clinicians to paste into the hospital's own systems (SIS,
 * SIGGALEN), which this app has no integration with. Those portals mangle anything beyond plain
 * text, so the labels stay ASCII, the content keeps only line breaks, and empty sections (including
 * the "—" placeholder the form saves for an empty field) are left out.
 */
internal fun evolucionSisText(patient: Patient, evolucion: Evolucion): String {
    val diagnosticos = evolucion.diagnosticos
        .filter { it.descripcion.sisClean().isNotEmpty() }
        .joinToString("; ") { dx ->
            val code = dx.codigoCie10.sisClean()
            if (code.isEmpty()) dx.descripcion.sisClean() else "${dx.descripcion.sisClean()} ($code)"
        }
    val vitals = with(evolucion.vitals) {
        listOf("PA" to pa, "FC" to fc, "FR" to fr, "T" to temp, "SatO2" to satO2, "FiO2" to fio2)
    }
        .filter { (_, value) -> value.sisClean().isNotEmpty() }
        .joinToString(" | ") { (label, value) -> "$label: ${value.sisClean()}" }

    val lines = listOf(
        "=== EVOLUCION CLINICA - PSIQUIATRIA ===",
        line("FECHA/HORA:", "${evolucion.fecha} ${evolucion.hora}"),
        line("PACIENTE:", "${patient.fullName.sisClean()} - Historia: ${patient.medicalRecordNumber.sisClean()}"),
        line("MEDICO RESPONSABLE:", evolucion.medico),
        line("[SUBJETIVO]", evolucion.subjective),
        line("[OBJETIVO]", evolucion.objective),
        line("[ANALISIS]", evolucion.assessment),
        line("[PLAN]", evolucion.plan),
        line("[DIAGNOSTICOS]", diagnosticos),
        line("[TRATAMIENTO]", evolucion.treatmentText()),
        line("[SIGNOS VITALES]", vitals),
        SIS_RULE,
    )
    return lines.filterNotNull().joinToString("\n")
}

private fun line(label: String, value: String): String? = value.sisClean().takeIf { it.isNotEmpty() }?.let { "$label $it" }

/** Normalizes line breaks, turns tabs into spaces and drops any other control character. */
private fun String.sisClean(): String {
    val cleaned = replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\t', ' ')
        .filter { it == '\n' || !it.isISOControl() }
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trim()
    return if (cleaned == "—" || cleaned == "-") "" else cleaned
}
