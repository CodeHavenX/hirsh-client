package com.cramsan.hirsh.ui.screens.patientlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientListUiState(
    val isLoading: Boolean = true,
    val patients: List<Patient> = emptyList(),
)

class PatientListViewModel(private val patientRepository: PatientRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientListUiState())
    val uiState: StateFlow<PatientListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val patients = patientRepository.getPatients()
            _uiState.update { it.copy(isLoading = false, patients = patients) }
        }
    }
}
