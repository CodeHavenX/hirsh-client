package com.cramsan.hirsh.ui.screens.patientedit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.components.ReadOnlyField
import com.cramsan.hirsh.ui.components.RequiredFieldLabel
import com.cramsan.hirsh.ui.components.SelectField
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import org.koin.compose.viewmodel.koinViewModel

private val bloodTypeOptions = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")

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

    EditPatientScreenContent(
        uiState = uiState,
        patientId = patientId,
        onCancel = onCancel,
        onFirstNameChange = viewModel::onFirstNameChange,
        onLastNameChange = viewModel::onLastNameChange,
        onSecondLastNameChange = viewModel::onSecondLastNameChange,
        onPhoneChange = viewModel::onPhoneChange,
        onBloodTypeChange = viewModel::onBloodTypeChange,
        onSave = viewModel::save,
    )
}

/** All rendering lives here, taking [uiState] as plain data, so `*Previews.kt` never needs a real ViewModel. */
@Composable
internal fun EditPatientScreenContent(
    uiState: EditPatientUiState,
    patientId: String,
    onCancel: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onSecondLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onBloodTypeChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val patient = uiState.patient
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).testTag("screen_scroll_container")) {
        when {
            uiState.isLoading -> Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            patient == null -> Text("Paciente no encontrado: $patientId", style = MaterialTheme.typography.bodyMedium)
            else -> EditPatientForm(
                uiState = uiState,
                patientName = patient.fullName,
                onCancel = onCancel,
                onFirstNameChange = onFirstNameChange,
                onLastNameChange = onLastNameChange,
                onSecondLastNameChange = onSecondLastNameChange,
                onPhoneChange = onPhoneChange,
                onBloodTypeChange = onBloodTypeChange,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun EditPatientForm(
    uiState: EditPatientUiState,
    patientName: String,
    onCancel: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onSecondLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onBloodTypeChange: (String) -> Unit,
    onSave: () -> Unit,
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
                    value = uiState.firstName,
                    onValueChange = onFirstNameChange,
                    label = { RequiredFieldLabel("Nombres") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("edit_first_name_field"),
                )
                OutlinedTextField(
                    value = uiState.lastName,
                    onValueChange = onLastNameChange,
                    label = { RequiredFieldLabel("Apellido paterno") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("edit_last_name_field"),
                )
                OutlinedTextField(
                    value = uiState.secondLastName,
                    onValueChange = onSecondLastNameChange,
                    label = { Text("Apellido materno", fontSize = 12.sp, color = HissInk2) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("edit_second_last_name_field"),
                )
                // DNI/fecha de nacimiento/sexo are read-only: the real PATCH endpoint doesn't
                // accept identity fields at all (HISS-622, confirmed against the backend source)
                // -- an editable field here would silently fail to save.
                ReadOnlyField("DNI", uiState.documentNumber)
                ReadOnlyField("Fecha de nacimiento", uiState.birthDate)
                ReadOnlyField("Sexo", uiState.sex?.toDisplayLabel().orEmpty())
                OutlinedTextField(
                    value = uiState.phone,
                    onValueChange = onPhoneChange,
                    label = { RequiredFieldLabel("Telefono de contacto") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("edit_phone_field"),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Datos medicos")
                SelectField(
                    label = { Text("Grupo sanguineo", fontSize = 12.sp, color = HissInk2) },
                    options = bloodTypeOptions,
                    selected = uiState.bloodType,
                    onSelect = onBloodTypeChange,
                )
                // Allergies are read-only here too: this repository doesn't reconcile the free-text
                // field against the real allergies sub-resource on update yet -- that's HISS-623's job.
                ReadOnlyField("Alergias conocidas", uiState.allergies)
            }
        }

        uiState.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !uiState.isSaving,
                shape = fieldShape,
                border = BorderStroke(1.5.dp, HissInk),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("edit_cancel_button"),
            ) {
                Text("Cancelar", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissInk)
            }
            Button(
                onClick = onSave,
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
