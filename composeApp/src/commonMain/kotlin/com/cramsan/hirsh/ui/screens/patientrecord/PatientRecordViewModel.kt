package com.cramsan.hirsh.ui.screens.patientrecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientRecordUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
)

class PatientRecordViewModel(private val patientRepository: PatientRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientRecordUiState())
    val uiState: StateFlow<PatientRecordUiState> = _uiState.asStateFlow()

    fun load(patientId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val patient = patientRepository.getPatient(patientId)
            _uiState.update { it.copy(isLoading = false, patient = patient) }
        }
    }
}
