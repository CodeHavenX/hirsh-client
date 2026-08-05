package com.cramsan.hirsh.ui.screens.patientlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.repository.PatientRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PatientListUiState(
    val isLoading: Boolean = true,
    val patients: List<Patient> = emptyList(),
)

class PatientListViewModel(private val patientRepository: PatientRepository) : ViewModel() {

    val uiState: StateFlow<PatientListUiState> = patientRepository.patients
        .map { patients -> PatientListUiState(isLoading = false, patients = patients) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientListUiState())
}
