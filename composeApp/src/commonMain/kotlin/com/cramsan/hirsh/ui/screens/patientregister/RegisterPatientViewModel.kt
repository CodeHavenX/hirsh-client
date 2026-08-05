package com.cramsan.hirsh.ui.screens.patientregister

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterPatientUiState(
    val name: String = "",
    val nationalId: String = "",
    val dateOfBirth: String = "",
    val phone: String = "",
    val sex: Sex? = null,
    val bloodType: String = "",
    val allergies: String = "",
    val assignedDoctor: String = "",
    val duplicateWarning: Patient? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val registeredPatientId: String? = null,
)

class RegisterPatientViewModel(private val patientRepository: PatientRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterPatientUiState())
    val uiState: StateFlow<RegisterPatientUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }
    fun onNationalIdChange(value: String) = _uiState.update { it.copy(nationalId = value) }
    fun onDateOfBirthChange(value: String) = _uiState.update { it.copy(dateOfBirth = value) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value) }
    fun onSexChange(value: Sex) = _uiState.update { it.copy(sex = value) }
    fun onBloodTypeChange(value: String) = _uiState.update { it.copy(bloodType = value) }
    fun onAllergiesChange(value: String) = _uiState.update { it.copy(allergies = value) }
    fun onAssignedDoctorChange(value: String) = _uiState.update { it.copy(assignedDoctor = value) }

    /**
     * Mirrors the prototype's checkDuplicate(): a name substring match or an
     * exact DNI match against existing patients. Called on name/DNI blur,
     * not on every keystroke.
     */
    fun checkDuplicate() {
        val state = _uiState.value
        val match = patientRepository.patients.value.find { patient ->
            (state.name.isNotBlank() && patient.name.contains(state.name, ignoreCase = true)) ||
                (state.nationalId.isNotBlank() && patient.nationalId == state.nationalId)
        }
        _uiState.update { it.copy(duplicateWarning = match) }
    }

    fun register() {
        val state = _uiState.value
        val sex = state.sex
        if (state.name.isBlank() || state.nationalId.isBlank() || state.dateOfBirth.isBlank() ||
            state.phone.isBlank() || sex == null
        ) {
            _uiState.update { it.copy(error = "Completa los campos requeridos") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val created = patientRepository.addPatient(
                name = state.name,
                nationalId = state.nationalId,
                dateOfBirth = state.dateOfBirth,
                phone = state.phone,
                sex = sex,
                bloodType = state.bloodType,
                allergies = state.allergies,
                assignedDoctor = state.assignedDoctor,
            )
            _uiState.update { it.copy(isSaving = false, registeredPatientId = created.id) }
        }
    }
}
