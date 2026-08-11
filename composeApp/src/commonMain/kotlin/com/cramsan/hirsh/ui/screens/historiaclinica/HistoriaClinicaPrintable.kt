package com.cramsan.hirsh.ui.screens.historiaclinica

import androidx.compose.runtime.Composable
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.ui.components.PrintableKvLines
import com.cramsan.hirsh.ui.components.PrintableSection
import com.cramsan.hirsh.ui.components.PrintableSectionText
import com.cramsan.hirsh.ui.components.PrintableSummary

/**
 * Maps a Historia Clinica into [PrintableSummary] calls -- mirrors
 * `renderHistoriaClinicaPrint` in prototype/print.html, including its
 * per-section not-implemented/pending fallback copy.
 */
@Composable
fun HistoriaClinicaPrintable(
    patient: Patient,
    hospitalizacion: Hospitalizacion,
    onBack: () -> Unit,
) {
    val historiaClinica = hospitalizacion.historiaClinica
    PrintableSummary(
        title = "VISTA PREVIA · HISTORIA CLINICA",
        onBack = onBack,
        subtitle = "Historia Clinica · ${hospitalizacion.servicio}",
        metaItems = listOf(
            "Paciente" to patient.name,
            "DNI" to patient.nationalId,
            "Fecha de ingreso" to "${hospitalizacion.fechaIngreso} · ${hospitalizacion.horaIngreso}",
            "Medico responsable" to hospitalizacion.medicoResponsable,
            "N° Historia" to patient.id,
            "Cama" to hospitalizacion.cama,
        ),
        sections = HcSectionKey.entries.map { key ->
            PrintableSection(label = key.label) { HcPrintableSectionBody(key, historiaClinica) }
        },
        signatureName = hospitalizacion.medicoResponsable,
    )
}

@Composable
private fun HcPrintableSectionBody(key: HcSectionKey, historiaClinica: HistoriaClinica) {
    val complete = historiaClinica.isSectionComplete(key)
    when {
        !key.implemented -> PrintableSectionText("(Seccion aun no disponible en el sistema)")
        !complete -> PrintableSectionText("(Pendiente de completar)")
        key == HcSectionKey.MOTIVO_INGRESO -> PrintableSectionText(formatMotivoIngreso(historiaClinica.motivoIngreso.data ?: MotivoIngreso()))
        else -> PrintableKvLines(kvLinesFor(key, historiaClinica))
    }
}

private fun kvLinesFor(key: HcSectionKey, historiaClinica: HistoriaClinica): List<Pair<String, String>> {
    val values = fieldMapFor(key, historiaClinica).orEmpty()
    return HC_SECTION_FIELDS[key].orEmpty().map { spec -> spec.label to values[spec.key].orEmpty() }
}

private fun formatMotivoIngreso(data: MotivoIngreso): String {
    val checked = MOTIVO_INGRESO_OPTIONS.filter { data.isChecked(it.key) }.map { it.label }
    val base = checked.joinToString(", ").ifEmpty { "Ninguno marcado" }
    return if (data.otrosDetalle.isNotBlank()) "$base\nDetalle: ${data.otrosDetalle}" else base
}
