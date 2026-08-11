package com.cramsan.hirsh.ui.screens.evolucionview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.Hospitalizacion
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.PrintableSection
import com.cramsan.hirsh.ui.components.PrintableSectionText
import com.cramsan.hirsh.ui.components.PrintableSummary
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissRadiusDefault

/**
 * Maps an Evolucion into [PrintableSummary] calls -- mirrors
 * `renderEvolucionPrint` in prototype/print.html.
 */
@Composable
fun EvolucionPrintable(
    patient: Patient,
    hospitalizacion: Hospitalizacion,
    evolucion: Evolucion,
    onBack: () -> Unit,
) {
    PrintableSummary(
        title = "VISTA PREVIA · NOTA DE EVOLUCION",
        onBack = onBack,
        subtitle = "Nota de evolucion · ${hospitalizacion.servicio}",
        metaItems = listOf(
            "Paciente" to patient.name,
            "DNI" to patient.nationalId,
            "Fecha de evolucion" to "${evolucion.fecha} · ${evolucion.hora}",
            "Medico" to evolucion.medico,
            "N° Historia" to patient.id,
            "Cama" to hospitalizacion.cama,
            "Pronostico" to evolucion.pronostico.toDisplayLabel(),
            "Evolucion" to evolucion.resultado.toDisplayLabel(),
        ),
        sections = evolucionPrintableSections(evolucion),
        signatureName = evolucion.medico,
    )
}

private fun evolucionPrintableSections(evolucion: Evolucion): List<PrintableSection> = buildList {
    add(PrintableSection("Diagnosticos") { PrintableSectionText(diagnosticosText(evolucion)) })
    add(PrintableSection("Subjetivo") { PrintableSectionText(evolucion.subjective.ifBlank { "—" }) })
    add(PrintableSection("Objetivo") { PrintableSectionText(evolucion.objective.ifBlank { "—" }) })
    add(PrintableSection("Analisis") { PrintableSectionText(evolucion.assessment.ifBlank { "—" }) })
    add(PrintableSection("Plan") { PrintableSectionText(evolucion.plan.ifBlank { "—" }) })
    add(PrintableSection("Prescripcion") { PrintableRxBox(evolucion.rx.ifBlank { "—" }) })
    add(PrintableSection("Examenes y resultados") { PrintableSectionText(examenesText(evolucion)) })
    if (evolucion.examenesObs.isNotBlank()) {
        add(PrintableSection("Observaciones / informe") { PrintableSectionText(evolucion.examenesObs) })
    }
}

private fun diagnosticosText(evolucion: Evolucion): String {
    if (evolucion.diagnosticos.isEmpty()) return "—"
    return evolucion.diagnosticos.joinToString("\n") { dx ->
        if (dx.codigoCie10.isNotBlank() && dx.codigoCie10 != "—") "${dx.descripcion} (${dx.codigoCie10})" else dx.descripcion
    }
}

private fun examenesText(evolucion: Evolucion): String {
    if (evolucion.examenes.isEmpty()) return "Sin examenes registrados."
    return evolucion.examenes.joinToString("\n") { examen ->
        val unidad = examen.unidad.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        val referencia = examen.referencia.takeIf { it.isNotBlank() }?.let { " (ref: $it)" }.orEmpty()
        val fecha = examen.fecha.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        "${examen.tipo}: ${examen.nombre} — ${examen.resultado.ifBlank { "—" }}$unidad$referencia$fecha"
    }
}

@Composable
private fun PrintableRxBox(text: String) {
    Surface(
        shape = RoundedCornerShape(HissRadiusDefault),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, HissFaint),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}
