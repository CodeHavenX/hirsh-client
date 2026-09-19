package com.cramsan.hirsh.ui.screens.patientregister

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterPatientUiState(
    val fullName: String = "",
    val documentNumber: String = "",
    val birthDate: String = "",
    val phone: String = "",
    val sex: Sex? = null,
    val bloodType: String = "",
    val allergies: String = "",
    val duplicateWarning: Patient? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val registeredPatientId: String? = null,
)

class RegisterPatientViewModel(private val patientRepository: PatientRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterPatientUiState())
    val uiState: StateFlow<RegisterPatientUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(fullName = value) }
    fun onNationalIdChange(value: String) = _uiState.update { it.copy(documentNumber = value) }
    fun onDateOfBirthChange(value: String) = _uiState.update { it.copy(birthDate = value) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value) }
    fun onSexChange(value: Sex) = _uiState.update { it.copy(sex = value) }
    fun onBloodTypeChange(value: String) = _uiState.update { it.copy(bloodType = value) }
    fun onAllergiesChange(value: String) = _uiState.update { it.copy(allergies = value) }

    /**
     * Mirrors the prototype's checkDuplicate(): a name substring match or an
     * exact DNI match against existing patients. Called on name/DNI blur,
     * not on every keystroke.
     */
    fun checkDuplicate() {
        val state = _uiState.value
        val match = patientRepository.patients.value.find { patient ->
            (state.fullName.isNotBlank() && patient.fullName.contains(state.fullName, ignoreCase = true)) ||
                (state.documentNumber.isNotBlank() && patient.documentNumber == state.documentNumber)
        }
        _uiState.update { it.copy(duplicateWarning = match) }
    }

    fun register() {
        val state = _uiState.value
        val sex = state.sex
        if (state.isSaving) {
            return
        }
        if (state.fullName.isBlank() || state.documentNumber.isBlank() || state.birthDate.isBlank() ||
            state.phone.isBlank() || sex == null
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
                    name = state.fullName,
                    documentType = DocumentType.NID,
                    documentNumber = state.documentNumber,
                    birthDate = state.birthDate,
                    phone = state.phone,
                    sex = sex,
                    bloodType = state.bloodType,
                    allergies = state.allergies,
                )
                _uiState.update { it.copy(isSaving = false, registeredPatientId = created.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "No se pudo registrar el paciente") }
            }
        }
    }
}
