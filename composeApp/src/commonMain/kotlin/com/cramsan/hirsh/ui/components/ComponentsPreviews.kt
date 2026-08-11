package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cramsan.hirsh.ui.preview.Preview
import com.cramsan.hirsh.ui.theme.HirshTheme

private data class SamplePatientRow(
    val id: String,
    val name: String,
    val doctor: String,
)

private val sampleRows = listOf(
    SamplePatientRow("HCL-2201", "Karla Ricaldi", "Dr. A. Patel"),
    SamplePatientRow("HCL-2202", "Jesus Mendoza", "Dr. R. Salas"),
)

@Preview
@Composable
private fun DataTablePreview() {
    HirshTheme {
        DataTable(
            columns = listOf(
                DataTableColumn<SamplePatientRow>(label = "HCL", weight = 0.6f) { Text(it.id) },
                DataTableColumn<SamplePatientRow>(label = "Nombre", weight = 2f) { Text(it.name) },
                DataTableColumn<SamplePatientRow>(label = "Medico", weight = 1f) { Text(it.doctor) },
            ),
            rows = sampleRows,
            onRowClick = {},
            isHighlighted = { it == sampleRows.first() },
        )
    }
}

@Preview
@Composable
private fun StatusBadgePreview() {
    HirshTheme {
        Column {
            StatusBadge("Activa", BadgeTone.Progress)
            Spacer(Modifier.height(4.dp))
            StatusBadge("Alta", BadgeTone.Done)
            Spacer(Modifier.height(4.dp))
            StatusBadge("Alergia", BadgeTone.Warn)
            Spacer(Modifier.height(4.dp))
            StatusBadge("Inactivo", BadgeTone.Off)
        }
    }
}

@Preview
@Composable
private fun ChipPreview() {
    HirshTheme {
        Chip("20 May 2026")
    }
}

@Preview
@Composable
private fun KeyValueRowPreview() {
    HirshTheme {
        KeyValueRow("DNI", "45123890")
    }
}

@Preview
@Composable
private fun ReadOnlyFieldPreview() {
    HirshTheme {
        ReadOnlyField("Subjetivo", "Paciente refiere mejoria del animo, sin ideacion suicida.")
    }
}

@Preview
@Composable
private fun VisitCardPreview() {
    HirshTheme {
        VisitCard(
            title = "Psiquiatria",
            meta = "20 May 2026 -> En curso · Dr. A. Patel",
            summary = "Episodio depresivo moderado con sintomas de ansiedad.",
            active = true,
            onClick = {},
            trailing = { StatusBadge("Activa", BadgeTone.Progress) },
        )
    }
}

@Preview
@Composable
private fun PrintableSummaryPreview() {
    HirshTheme {
        PrintableSummary(
            title = "VISTA PREVIA · HISTORIA CLINICA",
            onBack = {},
            subtitle = "Historia Clinica · Psiquiatria (UHSMA)",
            metaItems = listOf(
                "Paciente" to "Karla Sofia Ricaldi Sedano",
                "DNI" to "70083906",
                "Fecha de ingreso" to "20 May 2026 · 14:30",
                "Medico responsable" to "Dr. Reyes",
                "N° Historia" to "#00129",
                "Cama" to "01",
            ),
            sections = listOf(
                PrintableSection("Filiacion") {
                    PrintableKvLines(listOf("Edad" to "13 anos", "DNI" to "70083906"))
                },
                PrintableSection("Enfermedad Actual") {
                    PrintableSectionText("Episodio actual de heteroagresividad hacia los padres.")
                },
            ),
            signatureName = "Dr. Reyes",
        )
    }
}

@Preview
@Composable
private fun AppScaffoldPreview() {
    HirshTheme {
        AppScaffold(
            items = listOf(
                NavItem(label = "Pacientes", destination = "patients"),
                NavItem(label = "Cuentas", destination = "accounts"),
            ),
            selectedDestination = "patients",
            onNavigate = {},
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text("Contenido", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview
@Composable
private fun EncounterTopBarPreview() {
    HirshTheme {
        EncounterTopBar(
            title = "Evolucion",
            onClose = {},
            meta = {
                Chip("20 May 2026")
                Chip("Psiquiatria")
            },
            actions = {
                StatusBadge("En progreso", BadgeTone.Progress)
            },
        )
    }
}
