package com.cramsan.hirsh.ui.screens.patientedit

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
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import org.koin.compose.viewmodel.koinViewModel

private val bloodTypeOptions = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")
private val doctorOptions = listOf("Dr. Patel", "Dr. Reyes", "Dr. Lin")
private val FieldFontSize = 13.sp
private val fieldShape = RoundedCornerShape(HissRadiusDefault)

@Composable
fun EditPatientScreen(
    patientId: String,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: EditPatientViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(patientId) {
        viewModel.load(patientId)
    }
    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    val patient = uiState.patient
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            patient == null -> Text("Paciente no encontrado: $patientId", style = MaterialTheme.typography.bodyMedium)
            else -> EditPatientForm(uiState = uiState, patientName = patient.name, viewModel = viewModel, onCancel = onCancel)
        }
    }
}

@Composable
private fun EditPatientForm(
    uiState: EditPatientUiState,
    patientName: String,
    viewModel: EditPatientViewModel,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column {
            Text(
                "PACIENTES ›",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = HissInk2,
            )
            Text(
                "Editar paciente · $patientName",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Datos personales")
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { RequiredFieldLabel("Nombre completo") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("edit_name_field"),
                )
                OutlinedTextField(
                    value = uiState.nationalId,
                    onValueChange = viewModel::onNationalIdChange,
                    label = { RequiredFieldLabel("DNI") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("edit_dni_field"),
                )
                OutlinedTextField(
                    value = uiState.dateOfBirth,
                    onValueChange = viewModel::onDateOfBirthChange,
                    label = { RequiredFieldLabel("Fecha de nacimiento") },
                    placeholder = { Text("DD/MM/AAAA", fontSize = FieldFontSize) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("edit_dob_field"),
                )
                OutlinedTextField(
                    value = uiState.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = { RequiredFieldLabel("Telefono de contacto") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("edit_phone_field"),
                )
                SelectField(
                    label = { RequiredFieldLabel("Sexo") },
                    options = listOf("Masculino", "Femenino"),
                    selected = uiState.sex?.toDisplayLabel().orEmpty(),
                    onSelect = { label -> viewModel.onSexChange(if (label == "Masculino") Sex.MALE else Sex.FEMALE) },
                    modifier = Modifier.testTag("edit_sex_field"),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Datos medicos")
                SelectField(
                    label = { Text("Grupo sanguineo", fontSize = 12.sp, color = HissInk2) },
                    options = bloodTypeOptions,
                    selected = uiState.bloodType,
                    onSelect = viewModel::onBloodTypeChange,
                )
                OutlinedTextField(
                    value = uiState.allergies,
                    onValueChange = viewModel::onAllergiesChange,
                    label = { Text("Alergias conocidas", fontSize = 12.sp, color = HissInk2) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectField(
                    label = { Text("Medico asignado", fontSize = 12.sp, color = HissInk2) },
                    options = doctorOptions,
                    selected = uiState.assignedDoctor,
                    onSelect = viewModel::onAssignedDoctorChange,
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
                modifier = Modifier.testTag("edit_cancel_button"),
            ) {
                Text("Cancelar", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissInk)
            }
            Button(
                onClick = viewModel::save,
                enabled = !uiState.isSaving,
                shape = fieldShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.padding(start = 10.dp).testTag("edit_save_button"),
            ) {
                Text("Guardar cambios", fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
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
