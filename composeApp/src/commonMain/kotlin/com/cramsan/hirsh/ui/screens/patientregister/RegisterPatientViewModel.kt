package com.cramsan.hirsh.ui.screens.patientregister

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Allergy
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.assembleFullName
import com.cramsan.hirsh.ui.components.AllergyDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterPatientUiState(
    val medicalRecordNumber: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val secondLastName: String = "",
    val documentNumber: String = "",
    val birthDate: String = "",
    val phone: String = "",
    val sex: Sex? = null,
    val bloodType: String = "",
    /**
     * Allergies entered on this form, not yet saved anywhere: each one is posted to the allergies
     * sub-resource only after the patient itself is created (HISS-625). Ids are local (`local_N`).
     */
    val allergies: List<Allergy> = emptyList(),
    val allergyDraft: AllergyDraft? = null,
    /** The allergy whose "Quitar" is awaiting its inline confirmation. */
    val confirmingDeleteId: String? = null,
    val allergyError: String? = null,
    val duplicateWarning: Patient? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    /**
     * Set once the patient exists but some of its allergies failed to save. Registering again
     * would create a second patient, so from here the form is locked and the only ways forward are
     * [RegisterPatientViewModel.retryAllergies] (resends just the ones still in [allergies]) or
     * [RegisterPatientViewModel.goToRecord].
     */
    val createdPatientId: String? = null,
    val registeredPatientId: String? = null,
) {
    val isLocked: Boolean get() = createdPatientId != null
}

class RegisterPatientViewModel(private val patientRepository: PatientRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterPatientUiState())
    val uiState: StateFlow<RegisterPatientUiState> = _uiState.asStateFlow()

    fun onMedicalRecordNumberChange(value: String) = _uiState.update { it.copy(medicalRecordNumber = value) }
    fun onFirstNameChange(value: String) = _uiState.update { it.copy(firstName = value) }
    fun onLastNameChange(value: String) = _uiState.update { it.copy(lastName = value) }
    fun onSecondLastNameChange(value: String) = _uiState.update { it.copy(secondLastName = value) }
    fun onNationalIdChange(value: String) = _uiState.update { it.copy(documentNumber = value) }
    fun onDateOfBirthChange(value: String) = _uiState.update { it.copy(birthDate = value) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value) }
    fun onSexChange(value: Sex) = _uiState.update { it.copy(sex = value) }
    fun onBloodTypeChange(value: String) = _uiState.update { it.copy(bloodType = value) }

    private var nextLocalAllergyId = 1

    fun onStartAddAllergy() = _uiState.update {
        it.copy(allergyDraft = AllergyDraft(), confirmingDeleteId = null, allergyError = null)
    }

    fun onStartEditAllergy(allergy: Allergy) = _uiState.update {
        it.copy(
            allergyDraft = AllergyDraft(
                editingId = allergy.id,
                allergyType = allergy.allergyType,
                description = allergy.description,
                severity = allergy.severity,
                observations = allergy.observations,
            ),
            confirmingDeleteId = null,
            allergyError = null,
        )
    }

    // Nothing here is saved yet, so unlike EditPatientViewModel every field stays editable.
    fun onAllergyTypeChange(value: AllergyType) = updateDraft { it.copy(allergyType = value) }
    fun onAllergyDescriptionChange(value: String) = updateDraft { it.copy(description = value) }
    fun onAllergySeverityChange(value: Severity?) = updateDraft { it.copy(severity = value) }
    fun onAllergyObservationsChange(value: String) = updateDraft { it.copy(observations = value) }

    fun onCancelAllergyDraft() = _uiState.update { it.copy(allergyDraft = null, allergyError = null) }

    /** Adds/replaces the open draft in the local list -- nothing is sent until [register]. */
    fun saveAllergyDraft() {
        val state = _uiState.value
        val draft = state.allergyDraft ?: return
        val type = draft.allergyType
        if (type == null || draft.description.isBlank()) {
            _uiState.update { it.copy(allergyError = "Indica el tipo y la descripcion de la alergia") }
            return
        }
        val allergy = Allergy(
            id = draft.editingId ?: "local_${nextLocalAllergyId++}",
            allergyType = type,
            description = draft.description.trim(),
            severity = draft.severity,
            observations = draft.observations,
        )
        _uiState.update { current ->
            val allergies = if (draft.isNew) {
                current.allergies + allergy
            } else {
                current.allergies.map { if (it.id == allergy.id) allergy else it }
            }
            current.copy(allergies = allergies, allergyDraft = null, allergyError = null)
        }
    }

    fun onRequestDeleteAllergy(allergy: Allergy) = _uiState.update {
        it.copy(confirmingDeleteId = allergy.id, allergyDraft = null, allergyError = null)
    }

    fun onCancelDeleteAllergy() = _uiState.update { it.copy(confirmingDeleteId = null) }

    fun confirmDeleteAllergy() = _uiState.update { current ->
        current.copy(allergies = current.allergies.filterNot { it.id == current.confirmingDeleteId }, confirmingDeleteId = null)
    }

    private fun updateDraft(transform: (AllergyDraft) -> AllergyDraft) = _uiState.update { state ->
        state.allergyDraft?.let { state.copy(allergyDraft = transform(it)) } ?: state
    }

    /**
     * Mirrors the prototype's checkDuplicate(): a name substring match or an
     * exact DNI match against existing patients. Called on name/DNI blur,
     * not on every keystroke.
     */
    fun checkDuplicate() {
        val state = _uiState.value
        val name = assembleFullName(state.firstName, state.lastName, state.secondLastName)
        val match = patientRepository.patients.value.find { patient ->
            (name.isNotBlank() && patient.fullName.contains(name, ignoreCase = true)) ||
                (state.documentNumber.isNotBlank() && patient.documentNumber == state.documentNumber)
        }
        _uiState.update { it.copy(duplicateWarning = match) }
    }

    fun register() {
        val state = _uiState.value
        val sex = state.sex
        if (state.isSaving || state.isLocked) {
            return
        }
        if (state.medicalRecordNumber.isBlank() || state.firstName.isBlank() || state.lastName.isBlank() ||
            state.documentNumber.isBlank() || state.birthDate.isBlank() || state.phone.isBlank() || sex == null
        ) {
            _uiState.update { it.copy(error = "Completa los campos requeridos") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                // The registration form only ever collects a DNI (prototype/register.html never
                // offers a document-type picker), so DocumentType.NID is the only value this path
                // can produce until that form gains one.
                val created = patientRepository.addPatient(
                    medicalRecordNumber = state.medicalRecordNumber,
                    firstName = state.firstName,
                    lastName = state.lastName,
                    secondLastName = state.secondLastName,
                    documentType = DocumentType.NID,
                    documentNumber = state.documentNumber,
                    birthDate = state.birthDate,
                    phone = state.phone,
                    sex = sex,
                    bloodType = state.bloodType,
                    allergies = "",
                )
                saveAllergies(created.id, state.allergies)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "No se pudo registrar el paciente") }
            }
        }
    }

    /** Resends only the allergies that failed after the patient was already created. */
    fun retryAllergies() {
        val state = _uiState.value
        val patientId = state.createdPatientId ?: return
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch { saveAllergies(patientId, state.allergies) }
    }

    /** Leaves for the (already created) patient's record, abandoning any allergies that didn't save. */
    fun goToRecord() {
        val patientId = _uiState.value.createdPatientId ?: return
        _uiState.update { it.copy(registeredPatientId = patientId) }
    }

    /**
     * Posts each of [allergies] against the just-created [patientId]. Posted last-to-first: the
     * record lists allergies newest first, so this shows them in the order they were entered.
     * Keeps going past a failure so one bad entry doesn't block the rest.
     */
    private suspend fun saveAllergies(patientId: String, allergies: List<Allergy>) {
        val failedIds = mutableSetOf<String>()
        for (allergy in allergies.asReversed()) {
            try {
                patientRepository.addAllergy(patientId, allergy.allergyType, allergy.description, allergy.severity, allergy.observations)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedIds += allergy.id
            }
        }
        if (failedIds.isEmpty()) {
            _uiState.update { it.copy(isSaving = false, registeredPatientId = patientId) }
        } else {
            val failed = allergies.filter { it.id in failedIds }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    createdPatientId = patientId,
                    allergies = failed,
                    allergyDraft = null,
                    confirmingDeleteId = null,
                    error = "Paciente registrado, pero no se pudieron guardar ${failed.size} alergia(s)",
                )
            }
        }
    }
}
