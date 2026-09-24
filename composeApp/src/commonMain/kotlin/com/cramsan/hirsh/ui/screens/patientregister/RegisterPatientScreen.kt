package com.cramsan.hirsh.ui.screens.patientregister

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.model.toDisplayLabel
import com.cramsan.hirsh.ui.components.AddAllergyButton
import com.cramsan.hirsh.ui.components.AllergyActions
import com.cramsan.hirsh.ui.components.AllergyDraftForm
import com.cramsan.hirsh.ui.components.AllergyList
import com.cramsan.hirsh.ui.components.FieldFontSize
import com.cramsan.hirsh.ui.components.FormSectionCaption
import com.cramsan.hirsh.ui.components.RequiredFieldLabel
import com.cramsan.hirsh.ui.components.SelectField
import com.cramsan.hirsh.ui.components.fieldShape
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.ui.theme.HissWarn
import com.cramsan.hirsh.ui.theme.HissWarnWash
import org.koin.compose.viewmodel.koinViewModel

private val bloodTypeOptions = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")

@Composable
fun RegisterPatientScreen(
    onRegistered: (patientId: String) -> Unit,
    onCancel: () -> Unit,
    onViewExistingPatient: (patientId: String) -> Unit,
    viewModel: RegisterPatientViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.registeredPatientId) {
        uiState.registeredPatientId?.let(onRegistered)
    }

    RegisterPatientScreenContent(
        uiState = uiState,
        onCancel = onCancel,
        onViewExistingPatient = onViewExistingPatient,
        onMedicalRecordNumberChange = viewModel::onMedicalRecordNumberChange,
        onFirstNameChange = viewModel::onFirstNameChange,
        onLastNameChange = viewModel::onLastNameChange,
        onSecondLastNameChange = viewModel::onSecondLastNameChange,
        onNationalIdChange = viewModel::onNationalIdChange,
        onDateOfBirthChange = viewModel::onDateOfBirthChange,
        onPhoneChange = viewModel::onPhoneChange,
        onSexChange = viewModel::onSexChange,
        onCheckDuplicate = viewModel::checkDuplicate,
        onBloodTypeChange = viewModel::onBloodTypeChange,
        allergyActions = AllergyActions(
            onStartAdd = viewModel::onStartAddAllergy,
            onStartEdit = viewModel::onStartEditAllergy,
            onTypeChange = viewModel::onAllergyTypeChange,
            onDescriptionChange = viewModel::onAllergyDescriptionChange,
            onSeverityChange = viewModel::onAllergySeverityChange,
            onObservationsChange = viewModel::onAllergyObservationsChange,
            onCancelDraft = viewModel::onCancelAllergyDraft,
            onSaveDraft = viewModel::saveAllergyDraft,
            onRequestDelete = viewModel::onRequestDeleteAllergy,
            onConfirmDelete = viewModel::confirmDeleteAllergy,
            onCancelDelete = viewModel::onCancelDeleteAllergy,
        ),
        onRegister = viewModel::register,
        onRetryAllergies = viewModel::retryAllergies,
        onGoToRecord = viewModel::goToRecord,
    )
}

/** All rendering lives here, taking [uiState] as plain data, so `*Previews.kt` never needs a real ViewModel. */
@Composable
@Suppress("LongParameterList")
internal fun RegisterPatientScreenContent(
    uiState: RegisterPatientUiState,
    onCancel: () -> Unit,
    onViewExistingPatient: (patientId: String) -> Unit,
    onMedicalRecordNumberChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onSecondLastNameChange: (String) -> Unit,
    onNationalIdChange: (String) -> Unit,
    onDateOfBirthChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSexChange: (Sex) -> Unit,
    onCheckDuplicate: () -> Unit,
    onBloodTypeChange: (String) -> Unit,
    allergyActions: AllergyActions,
    onRegister: () -> Unit,
    onRetryAllergies: () -> Unit,
    onGoToRecord: () -> Unit,
) {
    // Once the patient exists (only some allergies failed), nothing on the form can change it anymore.
    val editable = !uiState.isLocked
    // Unspecified inherits the field's own (disabled) content color, the way RequiredFieldLabel already does.
    val optionalLabelColor = if (editable) HissInk2 else Color.Unspecified
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).testTag("screen_scroll_container"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text(
                "PACIENTES ›",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = HissInk2,
            )
            Text(
                "Registrar nuevo paciente",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
        }

        uiState.duplicateWarning?.let { duplicate ->
            DuplicateWarningBanner(duplicate = duplicate, onViewExisting = { onViewExistingPatient(duplicate.id) })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Datos personales")
                OutlinedTextField(
                    value = uiState.medicalRecordNumber,
                    onValueChange = onMedicalRecordNumberChange,
                    label = { RequiredFieldLabel("N° Historia Clinica") },
                    placeholder = { Text("Ej: HC-2026-004312", fontSize = FieldFontSize) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth().testTag("register_mrn_field"),
                )
                OutlinedTextField(
                    value = uiState.firstName,
                    onValueChange = onFirstNameChange,
                    label = { RequiredFieldLabel("Nombres") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) onCheckDuplicate() }
                        .testTag("register_first_name_field"),
                )
                OutlinedTextField(
                    value = uiState.lastName,
                    onValueChange = onLastNameChange,
                    label = { RequiredFieldLabel("Apellido paterno") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) onCheckDuplicate() }
                        .testTag("register_last_name_field"),
                )
                OutlinedTextField(
                    value = uiState.secondLastName,
                    onValueChange = onSecondLastNameChange,
                    label = { Text("Apellido materno", fontSize = 12.sp, color = optionalLabelColor) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth().testTag("register_second_last_name_field"),
                )
                OutlinedTextField(
                    value = uiState.documentNumber,
                    onValueChange = onNationalIdChange,
                    label = { RequiredFieldLabel("DNI") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) onCheckDuplicate() }
                        .testTag("register_dni_field"),
                )
                OutlinedTextField(
                    value = uiState.birthDate,
                    onValueChange = onDateOfBirthChange,
                    label = { RequiredFieldLabel("Fecha de nacimiento") },
                    placeholder = { Text("DD/MM/AAAA", fontSize = FieldFontSize) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth().testTag("register_dob_field"),
                )
                OutlinedTextField(
                    value = uiState.phone,
                    onValueChange = onPhoneChange,
                    label = { RequiredFieldLabel("Telefono de contacto") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = FieldFontSize),
                    shape = fieldShape,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth().testTag("register_phone_field"),
                )
                SelectField(
                    label = { RequiredFieldLabel("Sexo") },
                    options = listOf("Masculino", "Femenino"),
                    selected = uiState.sex?.toDisplayLabel().orEmpty(),
                    onSelect = { label -> onSexChange(if (label == "Masculino") Sex.MALE else Sex.FEMALE) },
                    testTag = "register_sex_field",
                    enabled = editable,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FormSectionCaption("Datos medicos (opcional)")
                SelectField(
                    label = { Text("Grupo sanguineo", fontSize = 12.sp, color = optionalLabelColor) },
                    options = bloodTypeOptions,
                    selected = uiState.bloodType,
                    onSelect = onBloodTypeChange,
                    enabled = editable,
                )
                RegisterAllergySection(uiState = uiState, actions = allergyActions, editable = editable)
            }
        }

        uiState.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        if (uiState.isLocked) {
            AllergyRetryFooter(isSaving = uiState.isSaving, onGoToRecord = onGoToRecord, onRetry = onRetryAllergies)
        } else {
            RegisterFooter(isSaving = uiState.isSaving, onCancel = onCancel, onRegister = onRegister)
        }
    }
}

/** Local allergy entries (HISS-625): nothing here is sent until "Registrar paciente". */
@Composable
private fun RegisterAllergySection(uiState: RegisterPatientUiState, actions: AllergyActions, editable: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Alergias conocidas", fontSize = 12.sp, color = HissInk2)
        if (editable) {
            AllergyList(
                allergies = uiState.allergies,
                enabled = !uiState.isSaving,
                confirmingDeleteId = uiState.confirmingDeleteId,
                onEdit = actions.onStartEdit,
                onRequestDelete = actions.onRequestDelete,
                onConfirmDelete = actions.onConfirmDelete,
                onCancelDelete = actions.onCancelDelete,
            )
            val draft = uiState.allergyDraft
            if (draft != null) {
                AllergyDraftForm(draft = draft, isBusy = uiState.isSaving, actions = actions, lockAgent = false)
            } else {
                AddAllergyButton(enabled = !uiState.isSaving, onClick = actions.onStartAdd)
            }
        } else {
            AllergyList(allergies = uiState.allergies)
        }
        uiState.allergyError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("allergy_error"))
        }
    }
}

@Composable
private fun RegisterFooter(isSaving: Boolean, onCancel: () -> Unit, onRegister: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        OutlinedButton(
            onClick = onCancel,
            enabled = !isSaving,
            shape = fieldShape,
            border = BorderStroke(1.5.dp, HissInk),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.testTag("register_cancel_button"),
        ) {
            Text("Cancelar", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissInk)
        }
        Button(
            onClick = onRegister,
            enabled = !isSaving,
            shape = fieldShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.padding(start = 10.dp).testTag("register_submit_button"),
        ) {
            Text("Registrar paciente", fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
        }
    }
}

/** Shown once the patient exists but some allergies didn't save -- registering again would duplicate the patient. */
@Composable
private fun AllergyRetryFooter(isSaving: Boolean, onGoToRecord: () -> Unit, onRetry: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        OutlinedButton(
            onClick = onGoToRecord,
            enabled = !isSaving,
            shape = fieldShape,
            border = BorderStroke(1.5.dp, HissInk),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.testTag("register_go_to_record_button"),
        ) {
            Text("Ir al registro", fontSize = FieldFontSize, fontWeight = FontWeight.Medium, color = HissInk)
        }
        Button(
            onClick = onRetry,
            enabled = !isSaving,
            shape = fieldShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.padding(start = 10.dp).testTag("register_retry_allergies_button"),
        ) {
            Text("Reintentar", fontSize = FieldFontSize, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DuplicateWarningBanner(duplicate: Patient, onViewExisting: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .border(1.5.dp, HissWarn, RoundedCornerShape(HissRadiusDefault))
            .background(HissWarnWash, RoundedCornerShape(HissRadiusDefault))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "Posible duplicado.",
                color = HissWarn,
                fontSize = FieldFontSize,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Un paciente con datos similares ya existe.",
                color = HissWarn,
                fontSize = FieldFontSize,
            )
            Text(
                "Ver registro existente",
                color = HissWarn,
                fontSize = FieldFontSize,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onViewExisting).testTag("register_duplicate_view_existing_link"),
            )
        }
    }
}
