package com.cramsan.hirsh.ui.screens.evolucionnew

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.Drug
import com.cramsan.hirsh.model.RouteOfAdministration
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.SelectField
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissWarn
import com.cramsan.hirsh.ui.theme.HissWarnWash

/** What the prescription rows can ask of [NuevaEvolucionViewModel]; previews leave them as no-ops. */
internal class PrescriptionCallbacks(
    val onAdd: () -> Unit = {},
    val onRemove: (Int) -> Unit = {},
    val onQueryChange: (Int, String) -> Unit = { _, _ -> },
    val onDrugSelected: (Int, Drug) -> Unit = { _, _ -> },
    val onDrugCleared: (Int) -> Unit = {},
    val onDoseChange: (Int, String) -> Unit = { _, _ -> },
    val onFrequencyChange: (Int, String) -> Unit = { _, _ -> },
    val onRouteChange: (Int, RouteOfAdministration) -> Unit = { _, _ -> },
    val onDurationChange: (Int, String) -> Unit = { _, _ -> },
    val onInstructionsChange: (Int, String) -> Unit = { _, _ -> },
)

/**
 * The evolucion's formulary prescriptions. Each becomes one of the patient's treatments when the
 * evolucion is signed, so a drug comes from the formulary (searched inline, like discharge's
 * diagnosis picker) and dose and frequency are required.
 */
@Composable
internal fun PrescriptionRows(
    rows: List<RxRow>,
    allergies: List<List<Allergy>>,
    search: DrugSearch?,
    callbacks: PrescriptionCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { index, row ->
            PrescriptionCard(
                index = index,
                row = row,
                allergies = allergies.getOrNull(index).orEmpty(),
                search = search?.takeIf { it.row == index },
                callbacks = callbacks,
            )
        }
        GhostSmallButton("+ Agregar medicamento", onClick = callbacks.onAdd, testTag = "evo_new_rx_add_button")
    }
}

@Composable
private fun PrescriptionCard(
    index: Int,
    row: RxRow,
    allergies: List<Allergy>,
    search: DrugSearch?,
    callbacks: PrescriptionCallbacks,
) {
    Surface(
        shape = fieldShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (allergies.isEmpty()) HissFaint else HissWarn),
        modifier = Modifier.fillMaxWidth().testTag("evo_new_rx_row_$index"),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    val drug = row.drug
                    if (drug == null) {
                        DrugSearchField(index, row.query, search, callbacks)
                    } else {
                        SelectedDrug(index, drug, onClear = { callbacks.onDrugCleared(index) })
                    }
                }
                RemoveRowButton(onClick = { callbacks.onRemove(index) }, testTag = "evo_new_rx_remove_$index")
            }
            if (allergies.isNotEmpty()) AllergyWarning(index, allergies)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RxTextField(row.dose, "Dosis *", "Ej: 50 mg", Modifier.weight(1f), "evo_new_rx_dose_$index") {
                    callbacks.onDoseChange(index, it)
                }
                RxTextField(row.frequency, "Frecuencia *", "Ej: Cada 24 h", Modifier.weight(1f), "evo_new_rx_frequency_$index") {
                    callbacks.onFrequencyChange(index, it)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectField(
                    label = { Text("Via", fontSize = 12.sp) },
                    options = RouteOfAdministration.entries.map { it.toDisplayLabel() },
                    selected = row.route?.toDisplayLabel().orEmpty(),
                    onSelect = { label ->
                        RouteOfAdministration.entries.find { it.toDisplayLabel() == label }?.let { callbacks.onRouteChange(index, it) }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "evo_new_rx_route_$index",
                )
                RxTextField(row.duration, "Duracion", "Ej: 30 dias", Modifier.weight(1f), "evo_new_rx_duration_$index") {
                    callbacks.onDurationChange(index, it)
                }
            }
            RxTextField(row.instructions, "Indicaciones", "Ej: Con el desayuno", Modifier.fillMaxWidth(), "evo_new_rx_instructions_$index") {
                callbacks.onInstructionsChange(index, it)
            }
        }
    }
}

@Composable
private fun DrugSearchField(index: Int, query: String, search: DrugSearch?, callbacks: PrescriptionCallbacks) {
    OutlinedTextField(
        value = query,
        onValueChange = { callbacks.onQueryChange(index, it) },
        label = { Text("Medicamento *", fontSize = 12.sp) },
        placeholder = { Text("Buscar en el formulario", fontSize = FieldFontSize) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
        shape = fieldShape,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("evo_new_rx_drug_field_$index"),
    )
    val results = search?.takeIf { it.query == query.trim() }?.results
    when {
        !results.isNullOrEmpty() -> Surface(
            shape = fieldShape,
            border = BorderStroke(1.dp, HissFaint),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Column {
                results.forEachIndexed { option, drug ->
                    if (option > 0) HorizontalDivider(color = HissFaint)
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { callbacks.onDrugSelected(index, drug) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("evo_new_rx_drug_option_${index}_$option"),
                    ) {
                        Text(drug.displayName, fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
                        drug.therapeuticGroup?.let { Text(it, fontSize = 11.sp, color = HissInk2) }
                    }
                }
            }
        }
        results != null && query.trim().length >= MIN_DRUG_QUERY_LENGTH -> Text(
            "Sin resultados en el formulario",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = HissInk2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SelectedDrug(index: Int, drug: Drug, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("evo_new_rx_drug_selected_$index")) {
        Column(modifier = Modifier.weight(1f)) {
            Text(drug.displayName, fontSize = FieldFontSize, fontWeight = FontWeight.SemiBold)
            drug.therapeuticGroup?.let { Text(it, fontSize = 11.sp, color = HissInk2) }
        }
        TextButton(onClick = onClear, modifier = Modifier.testTag("evo_new_rx_drug_clear_$index")) {
            Text("Cambiar", fontSize = FieldFontSize)
        }
    }
}

/** A warning, not a block: the prescriber may know the allergy doesn't apply, but must see it. */
@Composable
private fun AllergyWarning(index: Int, allergies: List<Allergy>) {
    Surface(
        shape = fieldShape,
        color = HissWarnWash,
        modifier = Modifier.fillMaxWidth().testTag("evo_new_rx_allergy_$index"),
    ) {
        Text(
            "Alergia registrada: " + allergies.joinToString(", ") { it.description },
            color = HissWarn,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun RxTextField(
    value: String,
    label: String,
    placeholder: String,
    modifier: Modifier,
    testTag: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = FieldFontSize) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
        shape = fieldShape,
        singleLine = true,
        modifier = modifier.testTag(testTag),
    )
}
