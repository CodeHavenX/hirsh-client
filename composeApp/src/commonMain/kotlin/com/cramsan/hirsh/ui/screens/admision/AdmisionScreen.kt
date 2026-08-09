package com.cramsan.hirsh.ui.screens.admision

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import org.koin.compose.viewmodel.koinViewModel

private val servicioOptions = listOf(
    "Emergencia - Topico de Medicina",
    "Medicina General",
    "Psiquiatria (UHSMA)",
    "Pediatria",
    "Gineco-Obstetricia",
    "Cirugia",
)
private val medicoOptions = listOf("Dr. Patel", "Dr. Reyes", "Dr. Lin", "Dr. Hirsh")
private val FieldFontSize = 13.sp
private val fieldShape = RoundedCornerShape(HissRadiusDefault)

@Composable
fun AdmisionScreen(
    patientId: String,
    onAdmitted: (hospitalizationId: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: AdmisionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(patientId) {
        viewModel.load(patientId)
    }
    LaunchedEffect(uiState.createdHospitalizationId) {
        uiState.createdHospitalizationId?.let(onAdmitted)
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text(
                "PACIENTES › HOSPITALIZACIONES ›",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = HissInk2,
            )
            val patientName = uiState.patient?.name.orEmpty()
            Text(
                "Nueva hospitalizacion · $patientName",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Datos de ingreso")
                SelectField(
                    label = { RequiredFieldLabel("Servicio") },
                    options = servicioOptions,
                    selected = uiState.servicio,
                    onSelect = viewModel::onServicioChange,
                    modifier = Modifier.testTag("admision_servicio_field"),
                )
                OutlinedTextField(
                    value = uiState.cama,
                    onValueChange = viewModel::onCamaChange,
                    label = { RequiredFieldLabel("Cama") },
                    placeholder = { Text("Ej: 12", fontSize = FieldFontSize) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("admision_cama_field"),
                )
                SelectField(
                    label = { RequiredFieldLabel("Medico responsable") },
                    options = medicoOptions,
                    selected = uiState.medicoResponsable,
                    onSelect = viewModel::onMedicoResponsableChange,
                    modifier = Modifier.testTag("admision_medico_field"),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Motivo de ingreso")
                OutlinedTextField(
                    value = uiState.motivo,
                    onValueChange = viewModel::onMotivoChange,
                    label = { RequiredFieldLabel("Motivo") },
                    placeholder = {
                        Text(
                            "Ej: Sintomas respiratorios, heteroagresividad, control de hipertension...",
                            fontSize = FieldFontSize,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("admision_motivo_field"),
                )
                Text(
                    "El detalle completo (antecedentes, examen, diagnostico) se documenta en la Historia " +
                        "Clinica de la hospitalizacion.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HissInk2,
                )
            }
        }

        uiState.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = onCancel,
                shape = fieldShape,
                border = BorderStroke(1.5.dp, HissInk),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("admision_cancel_button"),
            ) {
                Text("Cancelar", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissInk)
            }
            Button(
                onClick = viewModel::register,
                enabled = !uiState.isSaving,
                shape = fieldShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.padding(start = 10.dp).testTag("admision_submit_button"),
            ) {
                Text("Admitir paciente", fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun FormSectionCaption(text: String) {
    Column {
        Text(
            text.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Medium,
            color = HissAccent,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp), color = HissFaint)
    }
}

@Composable
private fun RequiredFieldLabel(text: String) {
    Text(
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = HissAccent)) { append(" *") }
        },
        fontSize = 12.sp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(
    label: @Composable () -> Unit,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = label,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
            shape = fieldShape,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
